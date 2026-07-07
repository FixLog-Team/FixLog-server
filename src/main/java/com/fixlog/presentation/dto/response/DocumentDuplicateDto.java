package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.DocumentEntity;

public record DocumentDuplicateDto(
        String newDocumentId,
        String folderId,
        String title
) {
    public static DocumentDuplicateDto from(DocumentEntity entity) {
        return new DocumentDuplicateDto(
                entity.getDocumentId(),
                entity.getFolderId(),
                entity.getTitle()
        );
    }
}
