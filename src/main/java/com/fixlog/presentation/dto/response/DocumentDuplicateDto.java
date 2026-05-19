package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.DocumentEntity;

public record DocumentDuplicateDto(
        String newDocumentId,
        String workspaceId,
        String folderId,
        String title
) {
    public static DocumentDuplicateDto from(DocumentEntity entity) {
        return new DocumentDuplicateDto(
                entity.getDocumentId(),
                entity.getWorkspaceId(),
                entity.getFolderId(),
                entity.getTitle()
        );
    }
}
