package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.InvitationStatus;
import com.fixlog.domain.model.WorkspaceInvitationEntity;
import com.fixlog.domain.model.WorkspaceRole;

import java.time.Instant;
import java.util.UUID;

public record InvitationDto(
        UUID id,
        UUID workspaceId,
        String email,
        WorkspaceRole role,
        InvitationStatus status,
        Instant expiresAt,
        Instant createAt,
        String invitedByName
) {
    public static InvitationDto of(WorkspaceInvitationEntity entity, String invitedByName) {
        return new InvitationDto(
                entity.getId(),
                entity.getWorkspaceId(),
                entity.getEmail(),
                entity.getRole(),
                entity.getStatus(),
                entity.getExpiresAt(),
                entity.getCreateAt(),
                invitedByName
        );
    }
}
