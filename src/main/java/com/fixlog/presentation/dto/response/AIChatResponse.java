package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.AIMessageEntity;

import java.util.List;

public record AIChatResponse(
        AIMessageDto userMessage,
        AIMessageDto assistantMessage,
        /** 답변 근거로 검색된 참고 문서. 관련 문서가 없으면 빈 목록. 이력에는 저장되지 않는다. */
        List<SearchResultDto> references
) {
    public static AIChatResponse from(AIMessageEntity userMessage, AIMessageEntity assistantMessage,
                                      List<SearchResultDto> references) {
        return new AIChatResponse(
                AIMessageDto.from(userMessage),
                AIMessageDto.from(assistantMessage),
                references
        );
    }
}
