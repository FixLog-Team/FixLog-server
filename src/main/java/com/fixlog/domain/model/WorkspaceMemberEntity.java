package com.fixlog.domain.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * 워크스페이스 소속과 운영권. 이 행이 없는 사용자는 콘텐츠 판정 트리에 진입하지 못한다.
 */
@Entity
@Table(name = "apj_workspace_member")
@IdClass(WorkspaceMemberId.class)
public class WorkspaceMemberEntity {

    @Id
    @Column(name = "workspace_id", length = 100)
    private String workspaceId;

    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 10, nullable = false)
    private WorkspaceRole role;

    @Column(name = "create_user", length = 100)
    private String createUser;

    @Column(name = "create_time", updatable = false)
    private Instant createTime;

    protected WorkspaceMemberEntity() {
    }

    public WorkspaceMemberEntity(String workspaceId, UUID userId, WorkspaceRole role, String createUser) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
        this.createUser = createUser;
        this.createTime = Instant.now();
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public UUID getUserId() {
        return userId;
    }

    public WorkspaceRole getRole() {
        return role;
    }

    public String getCreateUser() {
        return createUser;
    }

    public Instant getCreateTime() {
        return createTime;
    }
}
