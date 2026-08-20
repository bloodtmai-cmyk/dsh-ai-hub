package ai.dsh.hub.keymgmt;

import ai.dsh.hub.common.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_key_application")
public class ApiKeyApplication {
    @Id
    private UUID id;
    @Column(nullable = false, length = 12)
    private String workcode;
    @Column(nullable = false, length = 500)
    private String purpose;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status;
    @Column(nullable = false)
    private Instant submittedAt;
    private Instant reviewedAt;
    @Column(length = 12)
    private String reviewer;
    @Column(length = 500)
    private String decisionComment;
    @Version
    private long version;

    protected ApiKeyApplication() {
    }

    public ApiKeyApplication(String workcode, String purpose) {
        this.id = UUID.randomUUID();
        this.workcode = workcode;
        this.purpose = purpose;
        this.status = Status.PENDING;
        this.submittedAt = Instant.now();
    }

    public void approve(String reviewer, String comment) {
        requirePending();
        this.status = Status.APPROVED;
        this.reviewer = reviewer;
        this.decisionComment = comment;
        this.reviewedAt = Instant.now();
    }

    public void reject(String reviewer, String comment) {
        requirePending();
        this.status = Status.REJECTED;
        this.reviewer = reviewer;
        this.decisionComment = comment;
        this.reviewedAt = Instant.now();
    }

    public void markRevoked() {
        if (status != Status.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "KEY_NOT_ACTIVE", "只有已批准的 Key 可以吊销");
        }
        this.status = Status.REVOKED;
    }

    private void requirePending() {
        if (status != Status.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_ALREADY_REVIEWED", "申请已处理");
        }
    }

    public UUID getId() { return id; }
    public String getWorkcode() { return workcode; }
    public String getPurpose() { return purpose; }
    public Status getStatus() { return status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewer() { return reviewer; }
    public String getDecisionComment() { return decisionComment; }

    public enum Status { PENDING, APPROVED, REJECTED, REVOKED }
}
