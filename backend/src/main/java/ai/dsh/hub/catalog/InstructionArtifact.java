package ai.dsh.hub.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "instruction_artifact")
public class InstructionArtifact {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true)
    private UUID capabilityId;
    @Column(nullable = false, length = 180)
    private String fileName;
    @Column(nullable = false, length = 120)
    private String mediaType;
    @Column(nullable = false)
    private long sizeBytes;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;
    @Column(nullable = false, length = 40)
    private String uploadedBy;
    @Column(nullable = false)
    private Instant uploadedAt;

    protected InstructionArtifact() {
    }

    public InstructionArtifact(UUID capabilityId, byte[] content, String sha256, String uploadedBy) {
        this.id = UUID.randomUUID();
        this.capabilityId = capabilityId;
        this.fileName = "AGENTS.md";
        this.mediaType = "text/markdown; charset=utf-8";
        this.sizeBytes = content.length;
        this.sha256 = sha256;
        this.content = content.clone();
        this.uploadedBy = uploadedBy;
        this.uploadedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCapabilityId() { return capabilityId; }
    public String getFileName() { return fileName; }
    public String getMediaType() { return mediaType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public byte[] getContent() { return content.clone(); }
    public String getUploadedBy() { return uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
}
