package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.AuditAction;
import com.fixlog.domain.model.AuditLogEntity;
import com.fixlog.domain.model.AuditResult;
import com.fixlog.domain.model.PrincipalType;
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
        /** 권한 변경의 대상. 접근 기록에는 없다. */
        PrincipalType targetPrincipalType,
        UUID targetPrincipalId,
        String targetName,
        /** 변경 전후 요약. 예: "MEMBER → ADMIN" */
        String detail,
        /** 접근 기록이 아니라 권한을 바꾼 기록인지. 화면에서 두 종류를 갈라 보여준다. */
        boolean permissionChange,
        AuditResult result,
        boolean viaAdmin,
        Instant createAt
) {
    public static AuditLogDto of(AuditLogEntity entity, String actorName, String targetName) {
        return new AuditLogDto(
                entity.getId(),
                entity.getActorUserId(),
                actorName,
                entity.getAction(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getTargetPrincipalType(),
                entity.getTargetPrincipalId(),
                targetName,
                entity.getDetail(),
                entity.getAction().isPermissionChange(),
                entity.getResult(),
                entity.isViaAdmin(),
                entity.getCreateAt()
        );
    }
}
