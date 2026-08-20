package ai.dsh.hub.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "bundle_member", uniqueConstraints = @UniqueConstraint(
        name = "uq_bundle_member", columnNames = {"bundle_id", "member_id"}))
public class BundleMember {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID bundleId;
    @Column(nullable = false)
    private UUID memberId;
    @Column(nullable = false)
    private boolean required;
    @Column(nullable = false)
    private int sortOrder;

    protected BundleMember() {
    }

    public BundleMember(UUID bundleId, UUID memberId, boolean required, int sortOrder) {
        this.id = UUID.randomUUID();
        this.bundleId = bundleId;
        this.memberId = memberId;
        this.required = required;
        this.sortOrder = sortOrder;
    }

    public UUID getId() { return id; }
    public UUID getBundleId() { return bundleId; }
    public UUID getMemberId() { return memberId; }
    public boolean isRequired() { return required; }
    public int getSortOrder() { return sortOrder; }

    public void replaceMember(UUID memberId) {
        this.memberId = memberId;
    }
}
