package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.AIConversationEntity;
import org.springframework.data.domain.Page;

import java.util.List;

public record AIConversationPageDto(
        List<AIConversationDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static AIConversationPageDto from(Page<AIConversationEntity> page) {
        return new AIConversationPageDto(
                page.getContent().stream().map(AIConversationDto::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
