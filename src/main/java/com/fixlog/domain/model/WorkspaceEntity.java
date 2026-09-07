package com.fixlog.domain.model;

import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * 권한 트리의 루트 노드. 부모 체인 순회는 항상 이 노드에서 끝난다.
 * 그래서 base_access가 INHERIT일 수 없고, 기본값은 ALLOW다.
 */
@Entity
@Table(name = "apj_workspace")
public class WorkspaceEntity {

    @Id
    @Column(name = "workspace_id", length = 100)
    private String workspaceId;

    @Column(name = "workspace_name", length = 100, nullable = false)
    private String workspaceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_access", length = 10, nullable = false)
    private BaseAccess baseAccess;

    @Column(name = "usable")
    private Integer usable;

    @Column(name = "create_user", length = 100)
    private String createUser;

    @Column(name = "create_time", updatable = false)
    private Instant createTime;

    @Column(name = "update_user", length = 100)
    private String updateUser;

    @Column(name = "update_time")
    private Instant updateTime;

    protected WorkspaceEntity() {
    }

    public WorkspaceEntity(String workspaceId, String workspaceName, String createUser) {
        this.workspaceId = workspaceId;
        this.workspaceName = workspaceName;
        // 초대된 사용자는 별도 설정 없이 접근 가능한 것이 기본 정책이다.
        this.baseAccess = BaseAccess.ALLOW;
        this.usable = 1;
        this.createUser = createUser;
        this.createTime = Instant.now();
        this.updateUser = createUser;
        this.updateTime = Instant.now();
    }

    /**
     * 워크스페이스 전역 기본 정책을 바꾼다.
     * 루트에는 상속할 부모가 없으므로 INHERIT을 받을 수 없다.
     */
    public void changeBaseAccess(BaseAccess baseAccess, String updateUser) {
        if (baseAccess == null || baseAccess == BaseAccess.INHERIT) {
            throw new BusinessException(Code.INVALID_REQUEST,
                    "워크스페이스 루트는 상속받을 부모가 없으므로 INHERIT으로 설정할 수 없습니다.");
        }
        this.baseAccess = baseAccess;
        this.updateUser = updateUser;
        this.updateTime = Instant.now();
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public String getWorkspaceName() {
        return workspaceName;
    }

    public BaseAccess getBaseAccess() {
        return baseAccess == null ? BaseAccess.ALLOW : baseAccess;
    }

    public Integer getUsable() {
        return usable;
    }

    public String getCreateUser() {
        return createUser;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public String getUpdateUser() {
        return updateUser;
    }

    public Instant getUpdateTime() {
        return updateTime;
    }
}
