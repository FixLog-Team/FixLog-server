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
        Instant deletedAt
) {
    public static TrashItemDto from(DocumentEntity document) {
        return new TrashItemDto(ResourceType.DOCUMENT, document.getDocumentId(),
                document.getTitle(), document.getDeletedBy(), document.getDeletedAt());
    }

    public static TrashItemDto from(FolderEntity folder) {
        return new TrashItemDto(ResourceType.FOLDER, folder.getFolderId(),
                folder.getFolderName(), folder.getDeletedBy(), folder.getDeletedAt());
    }
}
