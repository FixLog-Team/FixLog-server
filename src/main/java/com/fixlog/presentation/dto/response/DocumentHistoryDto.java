package com.fixlog.presentation.dto.response;

import com.fixlog.application.repository.DocumentHistoryRepository.DocumentHistorySummary;
import com.fixlog.domain.model.DocumentHistorySource;

import java.time.Instant;

/** 히스토리 목록 항목. 본문(blocks)은 상세 조회에서만 내려간다. */
public record DocumentHistoryDto(
        String historyId,
        String title,
        DocumentHistorySource source,
        String createUser,
        Instant createTime
) {
    public static DocumentHistoryDto from(DocumentHistorySummary summary) {
        return new DocumentHistoryDto(
                summary.getHistoryId(),
                summary.getTitle(),
                summary.getSource(),
                summary.getCreateUser(),
                summary.getCreateTime()
        );
    }
}
