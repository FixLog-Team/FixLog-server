package com.fixlog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        return new WorkspaceEntity(owner.getUserName() + "의 워크스페이스", owner.getUserId());
    }

    public static WorkspaceEntity shared(String workspaceName) {
        return new WorkspaceEntity(workspaceName, null);
    }

    public void rename(String workspaceName) {
        this.workspaceName = workspaceName;
        this.updateAt = Instant.now();
    }

    public boolean isPersonal() {
        return personalOwnerId != null;
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
