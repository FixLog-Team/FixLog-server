package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.AIMessageEntity;
import com.fixlog.domain.model.AIMessageRole;
import com.fixlog.domain.model.AIMessageStatus;

import java.time.Instant;
import java.util.UUID;

public record AIMessageDto(
        UUID messageId,
        UUID conversationId,
        Integer messageSequence,
        AIMessageRole role,
        String content,
        AIMessageStatus status,
        Instant createTime,
        Instant completeTime
) {
    public static AIMessageDto from(AIMessageEntity entity) {
        return new AIMessageDto(
                entity.getMessageId(),
                entity.getConversationId(),
                entity.getMessageSequence(),
                entity.getRole(),
                entity.getContent(),
                entity.getStatus(),
                entity.getCreateTime(),
                entity.getCompleteTime()
        );
    }
}
