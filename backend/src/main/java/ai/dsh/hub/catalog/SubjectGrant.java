package ai.dsh.hub.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subject_grant")
public class SubjectGrant {
    @Id
    private UUID id;
    @Column(nullable = false, length = 12)
    private String workcode;
    @Column(nullable = false)
    private UUID capabilityId;
    @Column(nullable = false)
    private Instant validFrom;
    private Instant validUntil;
    @Column(nullable = false)
    private boolean enabled;
    @Column(nullable = false, length = 12)
    private String createdBy;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant revokedAt;

    protected SubjectGrant() {
    }

    public SubjectGrant(String workcode, UUID capabilityId, Instant validFrom, Instant validUntil, String createdBy) {
        this.id = UUID.randomUUID();
        this.workcode = workcode;
        this.capabilityId = capabilityId;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.enabled = true;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public void replaceWindow(Instant validFrom, Instant validUntil, String createdBy) {
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.enabled = true;
        this.revokedAt = null;
        this.createdBy = createdBy;
    }

    public void revoke() {
        this.enabled = false;
        this.revokedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getWorkcode() { return workcode; }
    public UUID getCapabilityId() { return capabilityId; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidUntil() { return validUntil; }
    public boolean isEnabled() { return enabled; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
