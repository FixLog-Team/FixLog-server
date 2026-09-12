package com.fixlog.presentation.dto.request;

import com.fixlog.domain.model.PermissionType;

public record FolderSettingsRequest(
        Boolean inheritFromParent,
        PermissionType baseAccess
) {
    public boolean resolvedInheritFromParent() {
        return inheritFromParent == null || inheritFromParent;
    }

    public PermissionType resolvedBaseAccess() {
        return baseAccess == null ? PermissionType.ALLOW : baseAccess;
    }
}
