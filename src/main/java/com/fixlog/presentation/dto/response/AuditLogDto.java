package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.AuditAction;
import com.fixlog.domain.model.AuditLogEntity;
import com.fixlog.domain.model.AuditResult;
import com.fixlog.domain.model.ResourceType;

import java.time.Instant;
import java.util.UUID;

public record AuditLogDto(
        UUID logId,
        UUID actorUserId,
        String actorName,
        AuditAction action,
        ResourceType resourceType,
        String resourceId,
        AuditResult result,
        boolean viaAdmin,
        Instant createAt
) {
    public static AuditLogDto of(AuditLogEntity entity, String actorName) {
        return new AuditLogDto(
                entity.getId(),
                entity.getActorUserId(),
                actorName,
                entity.getAction(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getResult(),
                entity.isViaAdmin(),
                entity.getCreateAt()
        );
    }
}
