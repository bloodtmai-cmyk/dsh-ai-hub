package ai.dsh.hub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JsonContractIntegrationTest {
    @Autowired JsonMapper mapper;

    @Test
    void uuidAndTimeRemainStringsAndNullFieldsRemainOmitted() {
        UUID id = UUID.randomUUID();
        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        var json = mapper.readTree(mapper.writeValueAsString(new Response(id, time, null)));
        assertThat(json.path("id").asText()).isEqualTo(id.toString());
        assertThat(json.path("createdAt").asText()).isEqualTo(time.toString());
        assertThat(json.has("optional")).isFalse();
    }

    private record Response(UUID id, Instant createdAt, String optional) {}
}
