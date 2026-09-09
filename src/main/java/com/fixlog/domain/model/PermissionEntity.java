package com.fixlog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * 주체(사용자·그룹)가 대상(폴더·문서)에 대해 갖는 접근 의도.
 *
 * <p>ALLOW는 접근 허용, DENY는 명시적 차단이다. 상속 체인 어디서든 DENY가 있으면
 * 더 위의 ALLOW를 무시하고 차단된다.
 */
@Entity
@Table(
        name = "permission",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_permission_target",
                columnNames = {"resource_type", "resource_id", "principal_type", "principal_id"})
)
public class PermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /** 판정과 관리자 조회를 워크스페이스로 좁히기 위해 비정규화해 둔다. */
    @Column(name = "workspace_id", columnDefinition = "uuid", nullable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "principal_type", length = 20, nullable = false)
    private PrincipalType principalType;

    @Column(name = "principal_id", columnDefinition = "uuid", nullable = false)
    private UUID principalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", length = 20, nullable = false)
    private ResourceType resourceType;

    /** 폴더·문서 ID는 varchar(100) 문자열이다. */
    @Column(name = "resource_id", length = 100, nullable = false)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_type", length = 10, nullable = false)
    private PermissionType permissionType;

    /** 열람은 허용하되 반출은 막는 설정을 표현한다. DENY 레코드에서는 무시된다. */
    @Column(name = "can_download", nullable = false)
    private boolean canDownload;

    /** 문서/폴더를 편집할 수 있는지. 생성자(creator)에게만 true로 설정된다. */
    @Column(name = "can_edit", nullable = false)
    private boolean canEdit;

    @Column(name = "granted_by", columnDefinition = "uuid")
    private UUID grantedBy;

    @Column(name = "create_at", updatable = false)
    private Instant createAt;

    @Column(name = "update_at")
    private Instant updateAt;

    protected PermissionEntity() {
    }

    /** ALLOW 권한 부여 생성자. */
    public PermissionEntity(UUID workspaceId,
                            PrincipalType principalType, UUID principalId,
                            ResourceType resourceType, String resourceId,
                            boolean canDownload, UUID grantedBy) {
        this(workspaceId, principalType, principalId, resourceType, resourceId,
                PermissionType.ALLOW, canDownload, grantedBy);
    }

    public PermissionEntity(UUID workspaceId,
                            PrincipalType principalType, UUID principalId,
                            ResourceType resourceType, String resourceId,
                            PermissionType permissionType, boolean canDownload,
                            UUID grantedBy) {
        this.workspaceId = workspaceId;
        this.principalType = principalType;
        this.principalId = principalId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.permissionType = permissionType;
        this.canDownload = canDownload;
        this.grantedBy = grantedBy;
        this.createAt = Instant.now();
        this.updateAt = Instant.now();
    }

    public void markAsCreator() {
        this.canEdit = true;
    }

    public void update(PermissionType permissionType, boolean canDownload) {
        this.permissionType = permissionType;
        this.canDownload = canDownload;
        this.updateAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public PrincipalType getPrincipalType() {
        return principalType;
    }

    public UUID getPrincipalId() {
        return principalId;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public PermissionType getPermissionType() {
        return permissionType;
    }

    public boolean isCanDownload() {
        return canDownload;
    }

    public boolean isCanEdit() {
        return canEdit;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }

    public Instant getCreateAt() {
        return createAt;
    }

    public Instant getUpdateAt() {
        return updateAt;
    }
}
