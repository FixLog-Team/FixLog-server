package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.PermissionSource;
import com.fixlog.domain.model.ResourceType;

public record EffectivePermissionDto(
        ResourceType resourceType,
        String resourceId,
        String resourceName,
        boolean access,
        PermissionSource source,
        String sourceDetail,
        boolean canDownload
) {
}
