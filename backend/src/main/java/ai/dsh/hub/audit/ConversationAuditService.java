package ai.dsh.hub.audit;

import ai.dsh.hub.admin.AdminAuditService;
import ai.dsh.hub.common.ApiException;
import ai.dsh.hub.common.CryptoService;
import ai.dsh.hub.config.AiHubProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationAuditService {
    private final ConversationAuditRepository repository;
    private final CryptoService cryptoService;
    private final AdminAuditService adminAuditService;
    private final int maxContentLength;

    public ConversationAuditService(ConversationAuditRepository repository, CryptoService cryptoService,
                                    AdminAuditService adminAuditService, AiHubProperties properties) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.adminAuditService = adminAuditService;
        this.maxContentLength = properties.audit().maxContentLength();
    }

    @Transactional
    public IngestResult ingest(String workcode, IngestCommand command) {
        var existing = repository.findByWorkcodeAndSessionIdAndTurnId(workcode, command.sessionId(), command.turnId());
        if (existing.isPresent()) {
            return new IngestResult(existing.get().getId(), false);
        }
        validateContent(command.userMessage(), "userMessage");
        validateContent(command.assistantMessage(), "assistantMessage");
        UUID id = UUID.randomUUID();
        ConversationAudit entity = new ConversationAudit(
                id,
                workcode,
                command.sessionId(),
                command.turnId(),
                command.clientInstallationId(),
                command.model(),
                cryptoService.encrypt(command.userMessage(), aad(id, "user")),
                cryptoService.encrypt(command.assistantMessage(), aad(id, "assistant")),
                command.startedAt(),
                command.completedAt(),
                command.inputTokens(),
                command.outputTokens(),
                command.latencyMs(),
                command.status(),
                command.errorCode());
        repository.save(entity);
        return new IngestResult(id, true);
    }

    @Transactional(readOnly = true)
    public Page<Summary> search(String workcode, ConversationAudit.Status status, Instant from, Instant to, Pageable pageable) {
        List<Specification<ConversationAudit>> filters = new ArrayList<>();
        if (workcode != null && !workcode.isBlank()) {
            filters.add((root, query, cb) -> cb.equal(root.get("workcode"), workcode));
        }
        if (status != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (from != null) {
            filters.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startedAt"), from));
        }
        if (to != null) {
            filters.add((root, query, cb) -> cb.lessThan(root.get("startedAt"), to));
        }
        return repository.findAll(Specification.allOf(filters), pageable).map(Summary::from);
    }

    @Transactional(readOnly = true)
    public Detail detail(UUID id, String adminWorkcode) {
        ConversationAudit entity = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AUDIT_NOT_FOUND", "对话审计不存在"));
        adminAuditService.record(adminWorkcode, "VIEW_CONVERSATION_CONTENT", "CONVERSATION_AUDIT",
                id.toString(), "SUCCESS", "workcode=" + entity.getWorkcode());
        return Detail.from(entity,
                cryptoService.decrypt(entity.getUserMessageCiphertext(), aad(id, "user")),
                cryptoService.decrypt(entity.getAssistantMessageCiphertext(), aad(id, "assistant")));
    }

    private void validateContent(String content, String field) {
        if (content.length() > maxContentLength) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "CONTENT_TOO_LARGE",
                    field + " 超过允许的审计长度");
        }
    }

    private static String aad(UUID id, String field) {
        return "conversation-audit:" + id + ":" + field;
    }

    public record IngestCommand(
            String sessionId,
            String turnId,
            String clientInstallationId,
            String model,
            String userMessage,
            String assistantMessage,
            Instant startedAt,
            Instant completedAt,
            long inputTokens,
            long outputTokens,
            Long latencyMs,
            ConversationAudit.Status status,
            String errorCode
    ) {
    }

    public record IngestResult(UUID id, boolean created) {
    }

    public record Summary(UUID id, String workcode, String sessionId, String turnId, String model,
                          Instant startedAt, Instant completedAt, long inputTokens, long outputTokens,
                          Long latencyMs, ConversationAudit.Status status, String errorCode) {
        static Summary from(ConversationAudit entity) {
            return new Summary(entity.getId(), entity.getWorkcode(), entity.getSessionId(), entity.getTurnId(),
                    entity.getModel(), entity.getStartedAt(), entity.getCompletedAt(), entity.getInputTokens(),
                    entity.getOutputTokens(), entity.getLatencyMs(), entity.getStatus(), entity.getErrorCode());
        }
    }

    public record Detail(UUID id, String workcode, String sessionId, String turnId, String clientInstallationId,
                         String model, String userMessage, String assistantMessage, Instant startedAt,
                         Instant completedAt, long inputTokens, long outputTokens, Long latencyMs,
                         ConversationAudit.Status status, String errorCode, Instant receivedAt) {
        static Detail from(ConversationAudit entity, String userMessage, String assistantMessage) {
            return new Detail(entity.getId(), entity.getWorkcode(), entity.getSessionId(), entity.getTurnId(),
                    entity.getClientInstallationId(), entity.getModel(), userMessage, assistantMessage,
                    entity.getStartedAt(), entity.getCompletedAt(), entity.getInputTokens(), entity.getOutputTokens(),
                    entity.getLatencyMs(), entity.getStatus(), entity.getErrorCode(), entity.getReceivedAt());
        }
    }
}
