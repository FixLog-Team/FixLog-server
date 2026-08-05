package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.SecurityPolicyEntity;

import java.util.UUID;

public record SecurityPolicyDto(
        UUID workspaceId,
        boolean allowSharing,
        boolean allowDownload,
        boolean enforceWatermark,
        int auditRetentionDays,
        int trashRetentionDays
) {
    public static SecurityPolicyDto from(SecurityPolicyEntity entity) {
        return new SecurityPolicyDto(
                entity.getWorkspaceId(),
                entity.isAllowSharing(),
                entity.isAllowDownload(),
                entity.isEnforceWatermark(),
                entity.getAuditRetentionDays(),
                entity.getTrashRetentionDays()
        );
    }
}
