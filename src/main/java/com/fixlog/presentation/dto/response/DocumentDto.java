package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.UserEntity;

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
        String createUserName,
        String createUserPictureUrl,
        Instant createTime,
        String updateUser,
        Instant updateTime,
        boolean orphaned,
        String originalFolderId
) {
    public static DocumentDto from(DocumentEntity entity) {
        return from(entity, null);
    }

    public static DocumentDto from(DocumentEntity entity, UserEntity author) {
        return new DocumentDto(
                entity.getDocumentId(),
                entity.getFolderId(),
                entity.getTitle(),
                entity.getBlocks(),
                entity.getPlainText(),
                entity.getContentHash(),
                entity.getOrdinal(),
                entity.getCreateUser(),
                author != null ? author.getUserName() : null,
                author != null ? author.getPictureUrl() : null,
                entity.getCreateTime(),
                entity.getUpdateUser(),
                entity.getUpdateTime(),
                false,
                null
        );
    }

    /** 실제 폴더에 접근할 수 없어 상위로 떠오른 항목. */
    public static DocumentDto orphaned(DocumentEntity entity) {
        return new DocumentDto(
                entity.getDocumentId(),
                entity.getFolderId(),
                entity.getTitle(),
                entity.getBlocks(),
                entity.getPlainText(),
                entity.getContentHash(),
                entity.getOrdinal(),
                entity.getCreateUser(),
                null,
                null,
                entity.getCreateTime(),
                entity.getUpdateUser(),
                entity.getUpdateTime(),
                true,
                entity.getFolderId()
        );
    }
}
