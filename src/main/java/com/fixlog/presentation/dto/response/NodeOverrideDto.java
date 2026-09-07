package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.AccessEffect;
import com.fixlog.domain.model.PermissionOverrideEntity;
import com.fixlog.domain.model.UserEntity;

import java.time.Instant;
import java.util.UUID;

public record NodeOverrideDto(
        UUID userId,
        String userName,
        String email,
        AccessEffect effect,
        Instant updateTime
) {
    public static NodeOverrideDto of(PermissionOverrideEntity override, UserEntity user) {
        return new NodeOverrideDto(
                override.getUserId(),
                user == null ? null : user.getUserName(),
                user == null ? null : user.getEmail(),
                override.getEffect(),
                override.getUpdateTime()
        );
    }
}
