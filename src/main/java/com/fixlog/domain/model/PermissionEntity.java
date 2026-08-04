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
 * 주체(사용자·그룹)가 대상(폴더·문서)에 대해 갖는 자격.
 *
 * <p>공유는 별도 개념이 아니라 이 레코드를 만드는 행위다. 폴더에 부여한 권한은
 * 하위로 상속되며 개별 문서에서 오버라이드할 수 있다.
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
    @Column(name = "permission_level", length = 20, nullable = false)
    private PermissionLevel permissionLevel;

    /** 레벨과 독립이다. 열람은 되지만 반출은 막는 설정을 표현한다 (FR-PRM-002). */
    @Column(name = "can_download", nullable = false)
    private boolean canDownload;

    @Column(name = "granted_by", columnDefinition = "uuid")
    private UUID grantedBy;

    @Column(name = "create_at", updatable = false)
    private Instant createAt;

    @Column(name = "update_at")
    private Instant updateAt;

    protected PermissionEntity() {
    }

    public PermissionEntity(UUID workspaceId,
                            PrincipalType principalType, UUID principalId,
                            ResourceType resourceType, String resourceId,
                            PermissionLevel permissionLevel, boolean canDownload,
                            UUID grantedBy) {
        this.workspaceId = workspaceId;
        this.principalType = principalType;
        this.principalId = principalId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.permissionLevel = permissionLevel;
        this.canDownload = canDownload;
        this.grantedBy = grantedBy;
        this.createAt = Instant.now();
        this.updateAt = Instant.now();
    }

    public void update(PermissionLevel permissionLevel, boolean canDownload) {
        this.permissionLevel = permissionLevel;
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

    public PermissionLevel getPermissionLevel() {
        return permissionLevel;
    }

    public boolean isCanDownload() {
        return canDownload;
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
