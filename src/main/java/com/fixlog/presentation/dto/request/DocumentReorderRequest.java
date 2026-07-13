package com.fixlog.presentation.dto.request;

import java.util.List;

/**
 * documentIds 순서대로 각 문서의 ordinal을 다시 매긴다. 각 documentId는 요청자 소유
 * 문서인지만 확인하며, folderId나 목록의 완전성(빠짐/중복)은 검증하지 않는다.
 */
public record DocumentReorderRequest(
        String folderId,
        List<String> documentIds
) {}
