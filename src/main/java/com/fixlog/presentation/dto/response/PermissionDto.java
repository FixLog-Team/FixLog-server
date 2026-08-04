package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.PermissionEntity;
import com.fixlog.domain.model.PermissionLevel;
import com.fixlog.domain.model.PrincipalType;

import java.time.Instant;
import java.util.UUID;

public record PermissionDto(
        UUID permissionId,
        PrincipalType principalType,
        UUID principalId,
        String principalName,
        PermissionLevel level,
        boolean canDownload,
        Instant createAt
) {
    public static PermissionDto of(PermissionEntity entity, String principalName) {
        return new PermissionDto(
                entity.getId(),
                entity.getPrincipalType(),
                entity.getPrincipalId(),
                principalName,
                entity.getPermissionLevel(),
                entity.isCanDownload(),
                entity.getCreateAt()
        );
    }
}
