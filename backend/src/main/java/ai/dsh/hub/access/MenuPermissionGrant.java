package ai.dsh.hub.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "menu_permission_grant")
public class MenuPermissionGrant {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubjectType subjectType;
    @Column(nullable = false, length = 120)
    private String subjectRef;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private MenuKey menuKey;
    @Column(nullable = false)
    private boolean enabled;
    @Column(nullable = false, length = 40)
    private String createdBy;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant revokedAt;

    protected MenuPermissionGrant() {
    }

    public MenuPermissionGrant(SubjectType subjectType, String subjectRef, MenuKey menuKey, String actor) {
        this.id = UUID.randomUUID();
        this.subjectType = subjectType;
        this.subjectRef = subjectRef;
        this.menuKey = menuKey;
        this.enabled = true;
        this.createdBy = actor;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void replace(SubjectType subjectType, String subjectRef, MenuKey menuKey, String actor) {
        this.subjectType = subjectType;
        this.subjectRef = subjectRef;
        this.menuKey = menuKey;
        this.enabled = true;
        this.createdBy = actor;
        this.revokedAt = null;
        this.updatedAt = Instant.now();
    }

    public void revoke() {
        this.enabled = false;
        this.revokedAt = Instant.now();
        this.updatedAt = revokedAt;
    }

    public UUID getId() { return id; }
    public SubjectType getSubjectType() { return subjectType; }
    public String getSubjectRef() { return subjectRef; }
    public MenuKey getMenuKey() { return menuKey; }
    public boolean isEnabled() { return enabled; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getRevokedAt() { return revokedAt; }

    public enum SubjectType { USER, DEPARTMENT }
    public enum MenuKey { PLUGIN_MARKET }
}
