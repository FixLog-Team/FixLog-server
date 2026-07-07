package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.DocumentEntity;
import org.springframework.data.domain.Page;

import java.util.List;

public record DocumentPageDto(
        List<DocumentDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static DocumentPageDto from(Page<DocumentEntity> page) {
        return new DocumentPageDto(
                page.getContent().stream().map(DocumentDto::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
