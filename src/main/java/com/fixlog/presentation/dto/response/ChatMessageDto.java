package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.ChatMessageEntity;

import java.time.Instant;

public record ChatMessageDto(
        String messageId,
        String role,
        String content,
        Instant createTime
) {
    public static ChatMessageDto from(ChatMessageEntity entity) {
        return new ChatMessageDto(
                entity.getMessageId(),
                entity.getRole().name(),
                entity.getContent(),
                entity.getCreateTime()
        );
    }
}
