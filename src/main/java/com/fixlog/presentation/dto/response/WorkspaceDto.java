package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.PermissionType;
import com.fixlog.domain.model.WorkspaceEntity;
import com.fixlog.domain.model.WorkspaceRole;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceDto(
        UUID workspaceId,
        String workspaceName,
        boolean personal,
        WorkspaceRole role,
        /** 구성원이 별도 설정 없이 접근할 수 있는지. 관리 화면에서 현재 노출 범위를 보여준다. */
        PermissionType baseAccess,
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
                entity.getBaseAccess(),
                entity.getCreateAt()
        );
    }
}
