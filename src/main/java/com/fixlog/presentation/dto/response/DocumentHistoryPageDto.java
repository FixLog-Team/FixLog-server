package com.fixlog.presentation.dto.response;

import com.fixlog.application.repository.DocumentHistoryRepository.DocumentHistorySummary;
import org.springframework.data.domain.Page;

import java.util.List;

public record DocumentHistoryPageDto(
        List<DocumentHistoryDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static DocumentHistoryPageDto from(Page<DocumentHistorySummary> page) {
        return new DocumentHistoryPageDto(
                page.getContent().stream().map(DocumentHistoryDto::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
