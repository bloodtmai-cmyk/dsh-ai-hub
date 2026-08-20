package ai.dsh.hub.release;

import ai.dsh.hub.common.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "desktop_release", uniqueConstraints = {
        @UniqueConstraint(name = "uq_desktop_release_platform_version", columnNames = {"platform", "version"})
})
public class DesktopRelease {
    @Id
    private UUID id;
    @Column(nullable = false, length = 80)
    private String version;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Platform platform;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status;
    private Boolean publishedSlot;
    @Column(nullable = false, length = 180)
    private String fileName;
    @Column(nullable = false, length = 120)
    private String mediaType;
    @Column(nullable = false)
    private long sizeBytes;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(length = 88)
    private String sha512;
    @Column(nullable = false, length = 100)
    private String artifactKey;
    @Column(nullable = false, columnDefinition = "text")
    private String releaseNotes;
    @Column(nullable = false, length = 40)
    private String createdBy;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(length = 40)
    private String publishedBy;
    private Instant publishedAt;
    private Instant supersededAt;

    protected DesktopRelease() {
    }

    public DesktopRelease(String version, Platform platform, String fileName, String mediaType, long sizeBytes,
                          String sha256, String sha512, String artifactKey, String releaseNotes, String createdBy) {
        this.id = UUID.randomUUID();
        this.version = version;
        this.platform = platform;
        this.status = Status.DRAFT;
        this.publishedSlot = null;
        this.fileName = fileName;
        this.mediaType = mediaType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.sha512 = sha512;
        this.artifactKey = artifactKey;
        this.releaseNotes = releaseNotes;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public void publish(String actor) {
        if (status != Status.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_DESKTOP_RELEASE_STATE",
                    "只有草稿客户端版本可以发布");
        }
        status = Status.PUBLISHED;
        publishedSlot = true;
        publishedBy = actor;
        publishedAt = Instant.now();
    }

    public void supersede() {
        if (status != Status.PUBLISHED) return;
        status = Status.SUPERSEDED;
        publishedSlot = null;
        supersededAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getVersion() { return version; }
    public Platform getPlatform() { return platform; }
    public Status getStatus() { return status; }
    public String getFileName() { return fileName; }
    public String getMediaType() { return mediaType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getSha512() { return sha512; }
    public String getArtifactKey() { return artifactKey; }
    public String getReleaseNotes() { return releaseNotes; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getPublishedBy() { return publishedBy; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getSupersededAt() { return supersededAt; }

    public enum Platform { MAC_ARM64, MAC_X64, WINDOWS_X64 }
    public enum Status { DRAFT, PUBLISHED, SUPERSEDED }
}
