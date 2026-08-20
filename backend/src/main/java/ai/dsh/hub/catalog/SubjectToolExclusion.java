package ai.dsh.hub.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subject_tool_exclusion")
public class SubjectToolExclusion {
    @Id
    private UUID id;
    @Column(nullable = false, length = 12)
    private String workcode;
    @Column(nullable = false)
    private UUID mcpCapabilityId;
    @Column(nullable = false)
    private UUID toolCapabilityId;
    @Column(nullable = false)
    private boolean enabled;
    @Column(nullable = false, length = 40)
    private String createdBy;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant revokedAt;

    protected SubjectToolExclusion() {
    }

    public SubjectToolExclusion(String workcode, UUID mcpCapabilityId, UUID toolCapabilityId, String createdBy) {
        this.id = UUID.randomUUID();
        this.workcode = workcode;
        this.mcpCapabilityId = mcpCapabilityId;
        this.toolCapabilityId = toolCapabilityId;
        this.enabled = true;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public void restore(String actor) {
        this.enabled = true;
        this.createdBy = actor;
        this.revokedAt = null;
    }

    public void revoke() {
        this.enabled = false;
        this.revokedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getWorkcode() { return workcode; }
    public UUID getMcpCapabilityId() { return mcpCapabilityId; }
    public UUID getToolCapabilityId() { return toolCapabilityId; }
    public boolean isEnabled() { return enabled; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
