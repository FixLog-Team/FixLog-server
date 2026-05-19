package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.DocumentEntity;

import java.time.Instant;

public record DocumentSaveStateDto(
        Instant lastSavedAt,
        String contentHash
) {
    public static DocumentSaveStateDto from(DocumentEntity entity) {
        return new DocumentSaveStateDto(
                entity.getUpdateTime(),
                entity.getContentHash()
        );
    }
}
