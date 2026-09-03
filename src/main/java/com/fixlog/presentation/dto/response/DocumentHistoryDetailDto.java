package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.DocumentHistoryEntity;
import com.fixlog.domain.model.DocumentHistorySource;

import java.time.Instant;

/** 히스토리 상세. 해당 시점의 블록 JSON 전문을 포함한다(미리보기·복원용). */
public record DocumentHistoryDetailDto(
        String historyId,
        String documentId,
        String title,
        String blocks,
        String contentHash,
        DocumentHistorySource source,
        String createUser,
        Instant createTime
) {
    public static DocumentHistoryDetailDto from(DocumentHistoryEntity entity) {
        return new DocumentHistoryDetailDto(
                entity.getHistoryId(),
                entity.getDocumentId(),
                entity.getTitle(),
                entity.getBlocks(),
                entity.getContentHash(),
                entity.getSource(),
                entity.getCreateUser(),
                entity.getCreateTime()
        );
    }
}
