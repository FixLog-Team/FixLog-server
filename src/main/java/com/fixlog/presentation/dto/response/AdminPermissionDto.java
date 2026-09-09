package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.PermissionEntity;
import com.fixlog.domain.model.PermissionType;
import com.fixlog.domain.model.PrincipalType;
import com.fixlog.domain.model.ResourceType;

import java.time.Instant;
import java.util.UUID;

/** 관리자 콘솔의 권한 현황 항목. ID만 나열하면 화면에서 쓸 수 없어 이름을 함께 담는다. */
public record AdminPermissionDto(
        UUID permissionId,
        ResourceType resourceType,
        String resourceId,
        String resourceName,
        PrincipalType principalType,
        UUID principalId,
        String principalName,
        PermissionType permissionType,
        boolean canDownload,
        boolean canEdit,
        Instant createAt
) {
    public static AdminPermissionDto of(PermissionEntity entity, String principalName, String resourceName) {
        return new AdminPermissionDto(
                entity.getId(),
                entity.getResourceType(),
                entity.getResourceId(),
                resourceName,
                entity.getPrincipalType(),
                entity.getPrincipalId(),
                principalName,
                entity.getPermissionType(),
                entity.isCanDownload(),
                entity.isCanEdit(),
                entity.getCreateAt()
        );
    }
}
