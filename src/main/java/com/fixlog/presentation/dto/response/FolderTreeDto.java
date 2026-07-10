package com.fixlog.presentation.dto.response;

import java.util.List;

/** 사이드바 트리 렌더링용. documentCount는 직속 문서 수이며 하위 폴더의 문서는 포함하지 않는다. */
public record FolderTreeDto(
        String folderId,
        String parentId,
        String folderName,
        Integer ordinal,
        long documentCount,
        List<FolderTreeDto> children
) {
}
