package ai.dsh.hub.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation_audit")
public class ConversationAudit {
    @Id
    private UUID id;
    @Column(nullable = false, length = 12)
    private String workcode;
    @Column(nullable = false, length = 100)
    private String sessionId;
    @Column(nullable = false, length = 100)
    private String turnId;
    @Column(nullable = false, length = 100)
    private String clientInstallationId;
    @Column(nullable = false, length = 120)
    private String model;
    @Column(nullable = false, columnDefinition = "text")
    private String userMessageCiphertext;
    @Column(nullable = false, columnDefinition = "text")
    private String assistantMessageCiphertext;
    @Column(nullable = false)
    private Instant startedAt;
    private Instant completedAt;
    @Column(nullable = false)
    private long inputTokens;
    @Column(nullable = false)
    private long outputTokens;
    private Long latencyMs;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status;
    @Column(length = 80)
    private String errorCode;
    @Column(nullable = false)
    private Instant receivedAt;

    protected ConversationAudit() {
    }

    public ConversationAudit(UUID id, String workcode, String sessionId, String turnId, String clientInstallationId,
                             String model, String userMessageCiphertext, String assistantMessageCiphertext,
                             Instant startedAt, Instant completedAt, long inputTokens, long outputTokens,
                             Long latencyMs, Status status, String errorCode) {
        this.id = id;
        this.workcode = workcode;
        this.sessionId = sessionId;
        this.turnId = turnId;
        this.clientInstallationId = clientInstallationId;
        this.model = model;
        this.userMessageCiphertext = userMessageCiphertext;
        this.assistantMessageCiphertext = assistantMessageCiphertext;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.status = status;
        this.errorCode = errorCode;
        this.receivedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getWorkcode() { return workcode; }
    public String getSessionId() { return sessionId; }
    public String getTurnId() { return turnId; }
    public String getClientInstallationId() { return clientInstallationId; }
    public String getModel() { return model; }
    public String getUserMessageCiphertext() { return userMessageCiphertext; }
    public String getAssistantMessageCiphertext() { return assistantMessageCiphertext; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public Long getLatencyMs() { return latencyMs; }
    public Status getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public Instant getReceivedAt() { return receivedAt; }

    public enum Status {
        SUCCESS, ERROR, CANCELLED
    }
}
