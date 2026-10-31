package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.WorkspaceEntity;
import com.fixlog.domain.model.WorkspaceRole;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceDto(
        UUID workspaceId,
        String workspaceName,
        boolean personal,
        WorkspaceRole role,
        Instant createAt
) {
    public static WorkspaceDto of(WorkspaceEntity entity, WorkspaceRole role) {
        if (entity == null) {
            return null;
        }
        return new WorkspaceDto(
                entity.getWorkspaceId(),
                entity.getWorkspaceName(),
                entity.isPersonal(),
                role,
                entity.getCreateAt()
        );
    }
}
