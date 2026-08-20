package ai.dsh.hub.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "client_plugin_artifact")
public class ClientPluginArtifact {
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
    @Column(nullable = false, columnDefinition = "text")
    private String manifestJson;
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;
    @Column(nullable = false, length = 40)
    private String uploadedBy;
    @Column(nullable = false)
    private Instant uploadedAt;

    protected ClientPluginArtifact() {
    }

    public ClientPluginArtifact(UUID capabilityId, String fileName, String mediaType, byte[] content,
                                String sha256, String manifestJson, String uploadedBy) {
        this.id = UUID.randomUUID();
        this.capabilityId = capabilityId;
        this.fileName = fileName;
        this.mediaType = mediaType;
        this.sizeBytes = content.length;
        this.sha256 = sha256;
        this.manifestJson = manifestJson;
        this.content = content.clone();
        this.uploadedBy = uploadedBy;
        this.uploadedAt = Instant.now();
    }

    public UUID getCapabilityId() { return capabilityId; }
    public String getFileName() { return fileName; }
    public String getMediaType() { return mediaType; }
    public String getSha256() { return sha256; }
    public String getManifestJson() { return manifestJson; }
    public byte[] getContent() { return content.clone(); }
}
