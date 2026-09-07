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
 * (워크스페이스, 사용자, 역할)의 결합. 한 사용자는 여러 워크스페이스에 속하며 역할은 각각 독립이다.
 *
 * <p>연관을 {@code @ManyToOne}이 아니라 raw UUID로 두는 이유는, 앞으로 권한 판정이 이 테이블을
 * 대량으로 조회하기 때문이다. 지연 로딩과 {@code open-in-view=false}가 얽히면 판정 경로에
 * 예측하기 어려운 초기화 예외가 생긴다. 사용자 정보가 필요한 지점에서만 명시적으로 조회한다.
 */
@Entity
@Table(
        name = "workspace_member",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_workspace_member", columnNames = {"workspace_id", "user_id"})
)
public class WorkspaceMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workspace_id", columnDefinition = "uuid", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private WorkspaceRole role;

    @Column(name = "create_at", updatable = false)
    private Instant createAt;

    protected WorkspaceMemberEntity() {
    }

    public WorkspaceMemberEntity(UUID workspaceId, UUID userId, WorkspaceRole role) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
        this.createAt = Instant.now();
    }

    public void changeRole(WorkspaceRole role) {
        this.role = role;
    }

    public boolean isAdminOrOwner() {
        return role == WorkspaceRole.ADMIN || role == WorkspaceRole.OWNER;
    }

    public boolean isOwner() {
        return role == WorkspaceRole.OWNER;
    }

    /** @deprecated isAdminOrOwner() 사용 */
    @Deprecated
    public boolean isAdmin() {
        return isAdminOrOwner();
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getUserId() {
        return userId;
    }

    public WorkspaceRole getRole() {
        return role;
    }

    public Instant getCreateAt() {
        return createAt;
    }
}
