package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.UserApiKeyEntity;

import java.time.Instant;
import java.util.UUID;

/** 키 원문 필드는 두지 않는다. 실수로라도 내보낼 경로를 만들지 않기 위함이다 (FR-AI-003). */
public record UserApiKeyDto(
        UUID keyId,
        String provider,
        String maskedKey,
        Instant createAt,
        Instant updateAt
) {
    public static UserApiKeyDto from(UserApiKeyEntity entity) {
        return new UserApiKeyDto(
                entity.getId(),
                entity.getProvider(),
                "****" + entity.getKeyHint(),
                entity.getCreateAt(),
                entity.getUpdateAt()
        );
    }
}
