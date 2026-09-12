package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.DocumentRevisionEntity;

import java.time.Instant;
import java.util.UUID;

/** 리비전 목록용. 본문은 상세 조회에서만 내려준다. */
public record DocumentRevisionDto(
        UUID revisionId,
        int revisionNo,
        String title,
        String contentHash,
        Integer restoredFromNo,
        String createUser,
        Instant createAt
) {
    public static DocumentRevisionDto from(DocumentRevisionEntity entity) {
        return new DocumentRevisionDto(
                entity.getId(),
                entity.getRevisionNo(),
                entity.getTitle(),
                entity.getContentHash(),
                entity.getRestoredFromNo(),
                entity.getCreateUser(),
                entity.getCreateAt()
        );
    }
}
