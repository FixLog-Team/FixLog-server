package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.AIConversationEntity;

import java.time.Instant;
import java.util.UUID;

public record AIConversationDto(
        UUID conversationId,
        String title,
        Instant createTime,
        Instant updateTime
) {
    public static AIConversationDto from(AIConversationEntity entity) {
        return new AIConversationDto(
                entity.getConversationId(),
                entity.getTitle(),
                entity.getCreateTime(),
                entity.getUpdateTime()
        );
    }
}
