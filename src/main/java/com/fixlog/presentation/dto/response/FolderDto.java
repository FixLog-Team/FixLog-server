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
        Instant updateTime
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
                entity.getUpdateTime()
        );
    }
}