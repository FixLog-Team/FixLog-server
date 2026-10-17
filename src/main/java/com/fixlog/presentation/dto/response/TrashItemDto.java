package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.domain.model.ResourceType;

import java.time.Instant;

/** 휴지통 목록 항목. 폴더와 문서를 한 목록에 섞어 보여준다. */
public record TrashItemDto(
        ResourceType resourceType,
        String resourceId,
        String name,
        String deletedBy,
        Instant deletedAt,
        /**
         * 직계 부모 폴더도 휴지통에 있을 때 그 폴더 ID. null이면 최상위 항목으로 표시한다.
         * 프론트엔드는 이 값으로 휴지통 내 트리 구조를 구성할 수 있다.
         */
        String trashedParentId
) {
    public static TrashItemDto from(DocumentEntity document, String trashedParentId) {
        return new TrashItemDto(ResourceType.DOCUMENT, document.getDocumentId(),
                document.getTitle(), document.getDeletedBy(), document.getDeletedAt(),
                trashedParentId);
    }

    public static TrashItemDto from(FolderEntity folder, String trashedParentId) {
        return new TrashItemDto(ResourceType.FOLDER, folder.getFolderId(),
                folder.getFolderName(), folder.getDeletedBy(), folder.getDeletedAt(),
                trashedParentId);
    }
}
