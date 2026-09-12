package com.fixlog.presentation.dto.request;

import com.fixlog.domain.model.PermissionType;
import com.fixlog.domain.model.PrincipalType;
import com.fixlog.domain.model.ResourceType;

import java.util.UUID;

public record AdminGrantPermissionRequest(
        ResourceType resourceType,
        String resourceId,
        PrincipalType principalType,
        UUID principalId,
        PermissionType permissionType,
        Boolean canDownload
) {
    public PermissionType resolvedPermissionType() {
        return permissionType == null ? PermissionType.ALLOW : permissionType;
    }

    public boolean resolvedCanDownload() {
        return canDownload == null || canDownload;
    }
}
