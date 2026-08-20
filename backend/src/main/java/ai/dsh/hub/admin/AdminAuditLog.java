package ai.dsh.hub.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLog {
    @Id
    private UUID id;
    @Column(nullable = false, length = 40)
    private String actor;
    @Column(nullable = false, length = 80)
    private String action;
    @Column(nullable = false, length = 80)
    private String targetType;
    @Column(length = 100)
    private String targetId;
    @Column(nullable = false, length = 24)
    private String outcome;
    @Column(length = 1000)
    private String detail;
    @Column(nullable = false)
    private Instant occurredAt;

    protected AdminAuditLog() {
    }

    public AdminAuditLog(String actor, String action, String targetType, String targetId, String outcome, String detail) {
        this.id = UUID.randomUUID();
        this.actor = actor;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.outcome = outcome;
        this.detail = detail;
        this.occurredAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getOutcome() { return outcome; }
    public String getDetail() { return detail; }
    public Instant getOccurredAt() { return occurredAt; }
}
