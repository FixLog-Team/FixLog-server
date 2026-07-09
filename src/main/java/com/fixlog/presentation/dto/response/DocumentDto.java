package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.DocumentEntity;

import java.time.Instant;

public record DocumentDto(
        String documentId,
        String folderId,
        String title,
        String blocks,
        String plainText,
        String contentHash,
        Integer ordinal,
        String createUser,
        Instant createTime,
        String updateUser,
        Instant updateTime
) {
    public static DocumentDto from(DocumentEntity entity) {
        return new DocumentDto(
                entity.getDocumentId(),
                entity.getFolderId(),
                entity.getTitle(),
                entity.getBlocks(),
                entity.getPlainText(),
                entity.getContentHash(),
                entity.getOrdinal(),
                entity.getCreateUser(),
                entity.getCreateTime(),
                entity.getUpdateUser(),
                entity.getUpdateTime()
        );
    }
}
