package com.fixlog.domain.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** {@link WorkspaceMemberEntity}의 복합 키. */
public class WorkspaceMemberId implements Serializable {

    private String workspaceId;
    private UUID userId;

    protected WorkspaceMemberId() {
    }

    public WorkspaceMemberId(String workspaceId, UUID userId) {
        this.workspaceId = workspaceId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkspaceMemberId other)) return false;
        return Objects.equals(workspaceId, other.workspaceId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workspaceId, userId);
    }
}
