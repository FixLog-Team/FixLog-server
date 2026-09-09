package com.fixlog.domain.model;

import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 협업·정책·관리자 권한의 경계. 모든 폴더·문서는 정확히 하나의 워크스페이스에 속한다.
 *
 * <p>개인 워크스페이스는 {@code personalOwnerId}로 구분한다. 별도의 boolean을 두지 않는 이유는
 * 두 값이 항상 같은 사실을 가리켜 어긋날 여지를 만들지 않기 위함이며,
 * 사용자당 하나만 존재한다는 제약을 UNIQUE 하나로 강제할 수 있기 때문이다.
 */
@Entity
@Table(name = "workspace")
public class WorkspaceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "workspace_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID workspaceId;

    @Column(name = "workspace_name", length = 100, nullable = false)
    private String workspaceName;

    /** 개인 워크스페이스의 소유자. 협업 워크스페이스는 null이다. */
    @Column(name = "personal_owner_id", columnDefinition = "uuid")
    private UUID personalOwnerId;

    /**
     * 상속 체인이 루트까지 올라갔을 때 적용할 기본 접근. 권한 트리의 종착점이다.
     *
     * <p>새 워크스페이스는 DENY로 시작한다. 권한 도구에서 불확실은 곧 누출이므로,
     * 명시적으로 부여한 것만 열리는 쪽을 기본으로 둔다. 전원 공개로 쓰려면
     * {@link #changeBaseAccess}로 ALLOW로 바꾼다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "base_access", length = 10, nullable = false)
    private PermissionType baseAccess = PermissionType.DENY;

    @Column(name = "create_at", updatable = false)
    private Instant createAt;

    @Column(name = "update_at")
    private Instant updateAt;

    protected WorkspaceEntity() {
    }

    private WorkspaceEntity(String workspaceName, UUID personalOwnerId) {
        this.workspaceName = workspaceName;
        this.personalOwnerId = personalOwnerId;
        this.createAt = Instant.now();
        this.updateAt = Instant.now();
    }

    public static WorkspaceEntity personalFor(UserEntity owner) {
        WorkspaceEntity workspace =
                new WorkspaceEntity(owner.getUserName() + "의 워크스페이스", owner.getUserId());
        // 개인 워크스페이스는 소유자 혼자 쓰므로 기본을 막아둘 이유가 없다.
        workspace.baseAccess = PermissionType.ALLOW;
        return workspace;
    }

    public static WorkspaceEntity shared(String workspaceName) {
        return new WorkspaceEntity(workspaceName, null);
    }

    public void rename(String workspaceName) {
        this.workspaceName = workspaceName;
        this.updateAt = Instant.now();
    }

    /** 구성원이 별도 설정 없이 접근할 수 있는지 바꾼다. */
    public void changeBaseAccess(PermissionType baseAccess) {
        if (baseAccess == null) {
            throw new BusinessException(Code.INVALID_REQUEST, "기본 접근 정책은 필수입니다.");
        }
        this.baseAccess = baseAccess;
        this.updateAt = Instant.now();
    }

    public boolean isPersonal() {
        return personalOwnerId != null;
    }

    public PermissionType getBaseAccess() {
        return baseAccess == null ? PermissionType.DENY : baseAccess;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getWorkspaceName() {
        return workspaceName;
    }

    public UUID getPersonalOwnerId() {
        return personalOwnerId;
    }

    public Instant getCreateAt() {
        return createAt;
    }

    public Instant getUpdateAt() {
        return updateAt;
    }
}
