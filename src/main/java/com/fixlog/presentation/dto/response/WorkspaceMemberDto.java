package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.WorkspaceMemberEntity;
import com.fixlog.domain.model.WorkspaceRole;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceMemberDto(
        UUID userId,
        String userName,
        String email,
        WorkspaceRole role,
        Instant joinedAt
) {
    public static WorkspaceMemberDto of(WorkspaceMemberEntity member, UserEntity user) {
        if (member == null || user == null) {
            return null;
        }
        return new WorkspaceMemberDto(
                user.getUserId(),
                user.getUserName(),
                user.getEmail(),
                member.getRole(),
                member.getCreateAt()
        );
    }
}
