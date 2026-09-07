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
        return new WorkspaceMemberDto(
                member.getUserId(),
                user == null ? null : user.getUserName(),
                user == null ? null : user.getEmail(),
                member.getRole(),
                member.getCreateTime()
        );
    }
}
