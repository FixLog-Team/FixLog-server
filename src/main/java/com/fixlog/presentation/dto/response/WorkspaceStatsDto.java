package com.fixlog.presentation.dto.response;

import java.util.Map;

/** 워크스페이스 문서·폴더 현황 (FR-ADM-005). */
public record WorkspaceStatsDto(
        long documentCount,
        long folderCount,
        long trashedDocumentCount,
        long trashedFolderCount,
        Map<String, Long> documentCountByUser
) {
}
