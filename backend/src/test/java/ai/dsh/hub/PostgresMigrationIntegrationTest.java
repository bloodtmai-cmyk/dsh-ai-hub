package ai.dsh.hub;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "aihub.postgres.required", matches = "true")
class PostgresMigrationIntegrationTest {
    @BeforeAll
    static void requireIsolatedDatabase() {
        assertThat(System.getenv("AI_HUB_TEST_DATABASE_URL"))
                .matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/dsh_boot4_test(?:\\?.*)?");
        assertThat(System.getenv("AI_HUB_TEST_DATABASE_USERNAME")).isNotBlank();
        assertThat(System.getenv("AI_HUB_TEST_DATABASE_PASSWORD")).isNotBlank();
        assertThat(System.getenv("AI_HUB_TEST_DATABASE_DRIVER")).isEqualTo("org.postgresql.Driver");
    }

    @Test
    void emptySchemaMigratesAndExistingHistoryRemainsValidOnRestart() {
        try (Fixture fixture = new Fixture()) {
            Flyway flyway = fixture.flyway(null);
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(9);
            flyway.validate();
            var history = fixture.jdbc.queryForList("select version, checksum from " + fixture.schema
                    + ".flyway_schema_history where success and version is not null order by installed_rank");
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(fixture.jdbc.queryForList("select version, checksum from " + fixture.schema
                    + ".flyway_schema_history where success and version is not null order by installed_rank"))
                    .isEqualTo(history);
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("9");
        }
    }

    @Test
    void existingV8DataSurvivesUpgradeAndMissingModelEndpointStaysUnset() {
        try (Fixture fixture = new Fixture()) {
            assertThat(fixture.flyway("8").migrate().migrationsExecuted).isEqualTo(8);
            UUID applicationId = UUID.randomUUID();
            UUID bindingId = UUID.randomUUID();
            UUID capabilityId = UUID.randomUUID();
            byte[] content = {0, 1, 2, 127, -1};
            fixture.jdbc.update("insert into " + fixture.schema + ".api_key_application "
                    + "(id, workcode, purpose, status, submitted_at, version) "
                    + "values (?, '12345', 'migration fixture', 'APPROVED', current_timestamp, 0)", applicationId);
            fixture.jdbc.update("insert into " + fixture.schema + ".api_key_binding "
                    + "(id, application_id, provider_key_id, secret_ciphertext, secret_hash, secret_mask, "
                    + "status, issued_at, version) values (?, ?, 'fixture', 'synthetic-ciphertext', "
                    + "'synthetic-hash', 'masked', 'AVAILABLE', current_timestamp, 0)", bindingId, applicationId);
            fixture.jdbc.update("insert into " + fixture.schema + ".capability "
                    + "(id, type, source_kind, external_ref, name, description, release_version, status, created_at, updated_at) "
                    + "values (?, 'CLIENT_PLUGIN', 'UPLOAD', 'migration-fixture', 'Fixture', 'Fixture', '1.0.0', "
                    + "'PUBLISHED', current_timestamp, current_timestamp)", capabilityId);
            fixture.jdbc.update("insert into " + fixture.schema + ".client_plugin_artifact "
                    + "(id, capability_id, file_name, media_type, size_bytes, sha256, manifest_json, content, uploaded_by, uploaded_at) "
                    + "values (?, ?, 'fixture.zip', 'application/zip', ?, 'fixture', '{}', ?, '1000000', current_timestamp)",
                    UUID.randomUUID(), capabilityId, content.length, content);

            Flyway upgraded = fixture.flyway(null);
            assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
            upgraded.validate();
            var binding = fixture.jdbc.queryForMap("select id, secret_ciphertext, status, provider, base_url from "
                    + fixture.schema + ".api_key_binding where id = ?", bindingId);
            assertThat(binding.get("id")).isEqualTo(bindingId);
            assertThat(binding.get("secret_ciphertext")).isEqualTo("synthetic-ciphertext");
            assertThat(binding.get("status")).isEqualTo("AVAILABLE");
            assertThat(binding.get("provider")).isNull();
            assertThat(binding.get("base_url")).isNull();
            assertThat(fixture.jdbc.queryForObject("select content from " + fixture.schema
                    + ".client_plugin_artifact where capability_id = ?", byte[].class, capabilityId)).isEqualTo(content);
            assertThat(upgraded.migrate().migrationsExecuted).isZero();
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final String schema = "boot4_fixture_" + UUID.randomUUID().toString().replace("-", "");
        private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
                System.getenv("AI_HUB_TEST_DATABASE_URL"), System.getenv("AI_HUB_TEST_DATABASE_USERNAME"),
                System.getenv("AI_HUB_TEST_DATABASE_PASSWORD"));
        private final JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Flyway flyway(String target) {
            var configuration = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema);
            if (target != null) configuration.target(target);
            return configuration.load();
        }

        @Override
        public void close() {
            // 仅清理本测试随机创建的 schema，不使用 Flyway clean 或删除共享业务表。
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }
}
