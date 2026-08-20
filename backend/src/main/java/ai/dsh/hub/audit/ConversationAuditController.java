package ai.dsh.hub.audit;

import ai.dsh.hub.common.IdentitySupport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/client/conversation-audits")
public class ConversationAuditController {
    private final ConversationAuditService service;

    public ConversationAuditController(ConversationAuditService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<IngestResponse> ingest(Authentication authentication, @Valid @RequestBody IngestRequest request) {
        String workcode = IdentitySupport.requireWorkcode(authentication);
        var result = service.ingest(workcode, request.toCommand());
        IngestResponse body = new IngestResponse(result.id(), result.created());
        return result.created()
                ? ResponseEntity.created(URI.create("/api/client/conversation-audits/" + result.id())).body(body)
                : ResponseEntity.ok(body);
    }

    public record IngestRequest(
            @NotBlank @Size(max = 100) String sessionId,
            @NotBlank @Size(max = 100) String turnId,
            @NotBlank @Size(max = 100) String clientInstallationId,
            @NotBlank @Size(max = 120) String model,
            @NotNull String userMessage,
            @NotNull String assistantMessage,
            @NotNull Instant startedAt,
            Instant completedAt,
            @PositiveOrZero long inputTokens,
            @PositiveOrZero long outputTokens,
            @PositiveOrZero Long latencyMs,
            @NotNull ConversationAudit.Status status,
            @Size(max = 80) String errorCode
    ) {
        ConversationAuditService.IngestCommand toCommand() {
            return new ConversationAuditService.IngestCommand(sessionId, turnId, clientInstallationId, model,
                    userMessage, assistantMessage, startedAt, completedAt, inputTokens, outputTokens, latencyMs,
                    status, errorCode);
        }
    }

    public record IngestResponse(UUID id, boolean created) {
    }
}
