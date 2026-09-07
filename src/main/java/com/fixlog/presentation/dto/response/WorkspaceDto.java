package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.BaseAccess;
import com.fixlog.domain.model.WorkspaceEntity;
import com.fixlog.domain.model.WorkspaceRole;

public record WorkspaceDto(
        String workspaceId,
        String workspaceName,
        WorkspaceRole role,
        BaseAccess baseAccess
) {
    public static WorkspaceDto from(WorkspaceEntity entity, WorkspaceRole role) {
        return new WorkspaceDto(
                entity.getWorkspaceId(),
                entity.getWorkspaceName(),
                role,
                entity.getBaseAccess()
        );
    }
}
