package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.AIMessageEntity;

public record AIChatResponse(
        AIMessageDto userMessage,
        AIMessageDto assistantMessage
) {
    public static AIChatResponse from(AIMessageEntity userMessage, AIMessageEntity assistantMessage) {
        return new AIChatResponse(
                AIMessageDto.from(userMessage),
                AIMessageDto.from(assistantMessage)
        );
    }
}
