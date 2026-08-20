package ai.dsh.hub.catalog;

import ai.dsh.hub.common.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "capability")
public class Capability {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Type type;
    private UUID parentId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SourceKind sourceKind;
    @Column(nullable = false, length = 240)
    private String externalRef;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 1000)
    private String description;
    @Column(nullable = false, length = 80)
    private String releaseVersion;
    @Column(length = 500)
    private String sourceRef;
    @Column(length = 128)
    private String integrityHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected Capability() {
    }

    public Capability(Type type, UUID parentId, SourceKind sourceKind, String externalRef, String name,
                      String description, String releaseVersion, String sourceRef, String integrityHash) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.parentId = parentId;
        this.sourceKind = sourceKind;
        this.externalRef = externalRef;
        this.name = name;
        this.description = description;
        this.releaseVersion = releaseVersion;
        this.sourceRef = sourceRef;
        this.integrityHash = integrityHash;
        this.status = Status.DISCOVERED;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void refreshDiscovery(UUID parentId, String name, String description, String sourceRef, String integrityHash) {
        this.parentId = parentId;
        this.name = name;
        this.description = description;
        this.sourceRef = sourceRef;
        this.integrityHash = integrityHash;
        this.updatedAt = Instant.now();
    }

    public void approve() {
        requireStatus(Status.DISCOVERED);
        this.status = Status.APPROVED;
        this.updatedAt = Instant.now();
    }

    public void publish() {
        requireStatus(Status.APPROVED);
        this.status = Status.PUBLISHED;
        this.updatedAt = Instant.now();
    }

    public void publishWithParent() {
        if (type != Type.TOOL) {
            throw new IllegalStateException("Only Tool lifecycle can follow its parent MCP");
        }
        this.status = Status.PUBLISHED;
        this.updatedAt = Instant.now();
    }

    public void disable() {
        this.status = Status.DISABLED;
        this.updatedAt = Instant.now();
    }

    private void requireStatus(Status expected) {
        if (status != expected) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_CAPABILITY_STATE",
                    "能力当前状态为 " + status + "，不能执行该操作");
        }
    }

    public UUID getId() { return id; }
    public Type getType() { return type; }
    public UUID getParentId() { return parentId; }
    public SourceKind getSourceKind() { return sourceKind; }
    public String getExternalRef() { return externalRef; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getReleaseVersion() { return releaseVersion; }
    public String getSourceRef() { return sourceRef; }
    public String getIntegrityHash() { return integrityHash; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public enum Type { MCP, TOOL, SKILL, BUNDLE, CLIENT_PLUGIN, INSTRUCTION }
    public enum SourceKind { GATEWAY, UPLOAD, MANUAL }
    public enum Status { DISCOVERED, APPROVED, PUBLISHED, DISABLED }
}
