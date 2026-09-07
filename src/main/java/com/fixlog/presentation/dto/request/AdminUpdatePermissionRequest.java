package com.fixlog.presentation.dto.request;

import com.fixlog.domain.model.PermissionType;

public record AdminUpdatePermissionRequest(
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
