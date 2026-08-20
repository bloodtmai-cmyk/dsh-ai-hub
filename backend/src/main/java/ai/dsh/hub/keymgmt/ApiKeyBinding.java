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
@Table(name = "api_key_binding")
public class ApiKeyBinding {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true)
    private UUID applicationId;
    @Column(nullable = false, length = 200)
    private String providerKeyId;
    @Column(nullable = false, columnDefinition = "text")
    private String secretCiphertext;
    @Column(nullable = false, length = 64)
    private String secretHash;
    @Column(nullable = false, length = 80)
    private String secretMask;
    @Column(length = 80)
    private String provider;
    @Column(length = 1000)
    private String baseUrl;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status;
    @Column(nullable = false)
    private Instant issuedAt;
    private Instant claimedAt;
    private Instant revokedAt;
    @Version
    private long version;

    protected ApiKeyBinding() {
    }

    public ApiKeyBinding(UUID id, UUID applicationId, String providerKeyId, String secretCiphertext,
                         String secretHash, String secretMask, String provider, String baseUrl) {
        this.id = id;
        this.applicationId = applicationId;
        this.providerKeyId = providerKeyId;
        this.secretCiphertext = secretCiphertext;
        this.secretHash = secretHash;
        this.secretMask = secretMask;
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.status = Status.AVAILABLE;
        this.issuedAt = Instant.now();
    }

    public void claim() {
        if (status != Status.AVAILABLE) {
            throw new ApiException(HttpStatus.GONE, "KEY_ALREADY_CLAIMED", "API Key 已领取或不可用");
        }
        this.status = Status.CLAIMED;
        this.claimedAt = Instant.now();
    }

    public void revoke() {
        if (status == Status.REVOKED) return;
        this.status = Status.REVOKED;
        this.revokedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public String getProviderKeyId() { return providerKeyId; }
    public String getSecretCiphertext() { return secretCiphertext; }
    public String getSecretHash() { return secretHash; }
    public String getSecretMask() { return secretMask; }
    public String getProvider() { return provider; }
    public String getBaseUrl() { return baseUrl; }
    public Status getStatus() { return status; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getClaimedAt() { return claimedAt; }
    public Instant getRevokedAt() { return revokedAt; }

    public Status getEffectiveStatus() {
        return status;
    }

    public boolean isUsable() {
        Status effectiveStatus = getEffectiveStatus();
        return effectiveStatus == Status.AVAILABLE || effectiveStatus == Status.CLAIMED;
    }

    public enum Status { AVAILABLE, CLAIMED, REVOKED }
}
