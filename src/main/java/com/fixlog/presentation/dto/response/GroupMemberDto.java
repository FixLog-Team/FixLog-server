package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.UserEntity;

import java.util.UUID;

public record GroupMemberDto(
        UUID userId,
        String userName,
        String email
) {
    public static GroupMemberDto from(UserEntity user) {
        if (user == null) {
            return null;
        }
        return new GroupMemberDto(user.getUserId(), user.getUserName(), user.getEmail());
    }
}
