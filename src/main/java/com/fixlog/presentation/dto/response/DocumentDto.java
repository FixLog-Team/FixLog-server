package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.DocumentEntity;

import java.time.Instant;

public record DocumentDto(
        String documentId,
        String folderId,
        String workspaceId,
        String title,
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
                entity.getWorkspaceId(),
                entity.getTitle(),
                entity.getOrdinal(),
                entity.getCreateUser(),
                entity.getCreateTime(),
                entity.getUpdateUser(),
                entity.getUpdateTime()
        );
    }
}
