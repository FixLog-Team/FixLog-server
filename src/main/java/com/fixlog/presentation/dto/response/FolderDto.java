package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.FolderEntity;

import java.time.Instant;

public record FolderDto(
        String folderId,
        String parentId,
        String folderName,
        Integer ordinal,
        String createUser,
        Instant createTime,
        String updateUser,
        Instant updateTime,
        boolean orphaned,
        String originalParentId
) {
    public static FolderDto from(FolderEntity entity) {
        return new FolderDto(
                entity.getFolderId(),
                entity.getParentId(),
                entity.getFolderName(),
                entity.getOrdinal(),
                entity.getCreateUser(),
                entity.getCreateTime(),
                entity.getUpdateUser(),
                entity.getUpdateTime(),
                false,
                null
        );
    }

    /** 실제 부모 폴더에 접근할 수 없어 상위로 떠오른 항목. */
    public static FolderDto orphaned(FolderEntity entity) {
        return new FolderDto(
                entity.getFolderId(),
                entity.getParentId(),
                entity.getFolderName(),
                entity.getOrdinal(),
                entity.getCreateUser(),
                entity.getCreateTime(),
                entity.getUpdateUser(),
                entity.getUpdateTime(),
                true,
                entity.getParentId()
        );
    }
}
