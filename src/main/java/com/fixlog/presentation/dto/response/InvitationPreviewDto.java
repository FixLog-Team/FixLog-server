package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.InvitationStatus;
import com.fixlog.domain.model.WorkspaceInvitationEntity;
import com.fixlog.domain.model.WorkspaceRole;

import java.time.Instant;

public record InvitationPreviewDto(
        String email,
        WorkspaceRole role,
        InvitationStatus status,
        Instant expiresAt,
        String workspaceName,
        String inviterName
) {
    public static InvitationPreviewDto of(WorkspaceInvitationEntity entity,
                                          String workspaceName,
                                          String inviterName) {
        return new InvitationPreviewDto(
                entity.getEmail(),
                entity.getRole(),
                entity.getStatus(),
                entity.getExpiresAt(),
                workspaceName,
                inviterName
        );
    }
}
