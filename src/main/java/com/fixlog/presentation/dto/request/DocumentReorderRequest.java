package com.fixlog.presentation.dto.request;

import java.util.List;

/**
 * folderId 안의 문서들의 새 순서. documentIds는 해당 폴더의 활성 문서 전체를 빠짐없이 담아야 한다.
 * folderId가 null이면 최상위 문서가 대상이다.
 */
public record DocumentReorderRequest(
        String folderId,
        List<String> documentIds
) {}
