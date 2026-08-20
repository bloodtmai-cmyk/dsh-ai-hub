package ai.dsh.hub;

import ai.dsh.hub.common.ApiException;
import ai.dsh.hub.keymgmt.ApiKeyApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ApiKeyLifecycleIntegrationTest {
    private static final String PROVIDER = "openai-compatible";
    private static final String BASE_URL = "https://models.example.com/v1";

    @Autowired
    ApiKeyApplicationService service;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void approvedKeyCanOnlyBeClaimedOnce() {
        var application = service.apply("12347", "Harness model access");
        String suppliedKey = "sk-manual-test-value-123456";
        var approved = service.approve(application.id(), "1000000", PROVIDER, BASE_URL, suppliedKey);

        assertThat(approved.secretMask()).startsWith("sk-manu");
        assertThat(approved.provider()).isEqualTo(PROVIDER);
        assertThat(approved.baseUrl()).isEqualTo(BASE_URL);
        assertThatThrownBy(() -> service.approve(application.id(), "1000000", PROVIDER, BASE_URL, suppliedKey))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("已处理");
        var claimed = service.claim(application.id(), "12347");
        assertThat(claimed.apiKey()).isEqualTo(suppliedKey);
        assertThat(claimed.provider()).isEqualTo(PROVIDER);
        assertThat(claimed.baseUrl()).isEqualTo(BASE_URL);
        assertThatThrownBy(() -> service.claim(application.id(), "12347"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("已领取");
        assertThat(service.listForUser("12347").getFirst().secretMask()).isEqualTo(claimed.mask());
    }

    @Test
    void repeatedApplicationReturnsTheExistingPendingRequest() {
        var first = service.apply("12357", "Harness model access");
        var repeated = service.apply("12357", "Harness model access");

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(repeated.status()).isEqualTo(first.status());
        assertThat(service.listForUser("12357")).hasSize(1);
    }

    @Test
    void administratorCanGrantAKeyBeforeTheUserApplies() {
        String suppliedKey = "sk-proactive-test-value-123456";
        var granted = service.grant("12358", "1000000", PROVIDER, BASE_URL, suppliedKey);

        assertThat(granted.status().name()).isEqualTo("APPROVED");
        assertThat(granted.purpose()).isEqualTo("管理员主动授权");
        assertThat(service.claim(granted.id(), "12358").apiKey()).isEqualTo(suppliedKey);
    }

    @Test
    void proactiveGrantDirectsPendingUsersBackToTheApprovalQueue() {
        service.apply("12359", "Harness model access");

        assertThatThrownBy(() -> service.grant("12359", "1000000", PROVIDER, BASE_URL,
                "sk-pending-test-value-123456"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("待审批申请");
        assertThat(service.listForUser("12359")).hasSize(1);
    }

    @Test
    void legacyBindingWithoutHubEndpointFailsClosed() {
        var application = service.apply("12360", "Harness model access");
        service.approve(application.id(), "1000000", PROVIDER, BASE_URL,
                "sk-legacy-test-value-123456");
        jdbcTemplate.update("update api_key_binding set base_url = null where application_id = ?",
                application.id());

        assertThatThrownBy(() -> service.claim(application.id(), "12360"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("模型访问地址尚未配置");
    }
}
