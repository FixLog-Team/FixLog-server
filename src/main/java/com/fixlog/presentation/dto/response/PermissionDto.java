package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.PermissionEntity;
import com.fixlog.domain.model.PermissionType;
import com.fixlog.domain.model.PrincipalType;

import java.time.Instant;
import java.util.UUID;

public record PermissionDto(
        UUID permissionId,
        PrincipalType principalType,
        UUID principalId,
        String principalName,
        PermissionType permissionType,
        boolean canDownload,
        boolean canEdit,
        /** null이면 직접 부여된 권한. 값이 있으면 해당 폴더에서 상속된 권한 (회수 불가). */
        String inheritedFromFolderId,
        Instant createAt
) {
    public static PermissionDto of(PermissionEntity entity, String principalName) {
        return new PermissionDto(
                entity.getId(),
                entity.getPrincipalType(),
                entity.getPrincipalId(),
                principalName,
                entity.getPermissionType(),
                entity.isCanDownload(),
                entity.isCanEdit(),
                null,
                entity.getCreateAt()
        );
    }

    public static PermissionDto ofInherited(PermissionEntity entity, String principalName,
                                             String fromFolderId) {
        return new PermissionDto(
                entity.getId(),
                entity.getPrincipalType(),
                entity.getPrincipalId(),
                principalName,
                entity.getPermissionType(),
                entity.isCanDownload(),
                entity.isCanEdit(),
                fromFolderId,
                entity.getCreateAt()
        );
    }
}
