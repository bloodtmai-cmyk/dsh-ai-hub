package ai.dsh.hub;

import ai.dsh.hub.admin.AdminAuditLogRepository;
import ai.dsh.hub.audit.ConversationAudit;
import ai.dsh.hub.audit.ConversationAuditRepository;
import ai.dsh.hub.audit.ConversationAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ConversationAuditIntegrationTest {
    @Autowired
    ConversationAuditService service;
    @Autowired
    ConversationAuditRepository repository;
    @Autowired
    AdminAuditLogRepository adminAuditRepository;

    @Test
    void conversationContentIsEncryptedIdempotentAndAccessAudited() {
        var command = new ConversationAuditService.IngestCommand(
                "session-a", "turn-a", "install-a", "deepseek-chat",
                "sensitive user content", "sensitive assistant content", Instant.now(), Instant.now(),
                12, 18, 250L, ConversationAudit.Status.SUCCESS, null);
        var first = service.ingest("12346", command);
        var second = service.ingest("12346", command);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.id()).isEqualTo(first.id());
        ConversationAudit stored = repository.findById(first.id()).orElseThrow();
        assertThat(stored.getUserMessageCiphertext()).doesNotContain("sensitive user content");

        var detail = service.detail(first.id(), "1000000");
        assertThat(detail.userMessage()).isEqualTo("sensitive user content");
        assertThat(adminAuditRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("VIEW_CONVERSATION_CONTENT");
            assertThat(log.getTargetId()).isEqualTo(first.id().toString());
        });
    }
}
