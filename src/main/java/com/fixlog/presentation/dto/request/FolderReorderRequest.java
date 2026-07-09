package com.fixlog.presentation.dto.request;

import java.util.List;

/**
 * parentId 하위 폴더들의 새 순서. folderIds는 해당 부모의 활성 폴더 전체를 빠짐없이 담아야 한다.
 * parentId가 null이면 최상위 폴더가 대상이다.
 */
public record FolderReorderRequest(
        String parentId,
        List<String> folderIds
) {}
