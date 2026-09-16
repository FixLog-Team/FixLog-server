package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.DocumentEntity;

import java.time.Instant;

public record FavoriteDocumentDto(
        String documentId,
        String folderId,
        String title,
        String updateUser,
        Instant updateTime
) {
    public static FavoriteDocumentDto from(DocumentEntity entity) {
        return new FavoriteDocumentDto(
                entity.getDocumentId(),
                entity.getFolderId(),
                entity.getTitle(),
                entity.getUpdateUser(),
                entity.getUpdateTime()
        );
    }
}
