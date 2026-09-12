package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.UserStatus;
import com.fixlog.domain.model.WorkspaceMemberEntity;
import com.fixlog.domain.model.WorkspaceRole;

import java.time.Instant;
import java.util.UUID;

public record AdminUserDto(
        UUID userId,
        String userName,
        String email,
        WorkspaceRole role,
        UserStatus userStatus,
        Instant joinedAt,
        Instant lastLoginAt
) {
    public static AdminUserDto of(WorkspaceMemberEntity member, UserEntity user) {
        if (user == null) return null;
        return new AdminUserDto(
                user.getUserId(),
                user.getUserName(),
                user.getEmail(),
                member.getRole(),
                user.getUserStatus(),
                member.getCreateAt(),
                user.getLastLoginAt()
        );
    }
}
