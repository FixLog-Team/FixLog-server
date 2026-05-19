package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.FolderEntity;

import java.time.Instant;

public class FolderDto {
    private String folderId;
    private String workspaceId;
    private String parentId;
    private String folderName;
    private Integer ordinal;
    private String createUser;
    private Instant createTime;
    private String updateUser;
    private Instant updateTime;

    public FolderDto() {
    }

    public static FolderDto from(FolderEntity entity) {
        FolderDto dto = new FolderDto();
        dto.folderId = entity.getFolderId();
        dto.workspaceId = entity.getWorkspaceId();
        dto.parentId = entity.getParentId();
        dto.folderName = entity.getFolderName();
        dto.ordinal = entity.getOrdinal();
        dto.createUser = entity.getCreateUser();
        dto.createTime = entity.getCreateTime();
        dto.updateUser = entity.getUpdateUser();
        dto.updateTime = entity.getUpdateTime();
        return dto;
    }

    public String getFolderId() {
        return folderId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public String getParentId() {
        return parentId;
    }

    public String getFolderName() {
        return folderName;
    }

    public Integer getOrdinal() {
        return ordinal;
    }

    public String getCreateUser() {
        return createUser;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public String getUpdateUser() {
        return updateUser;
    }

    public Instant getUpdateTime() {
        return updateTime;
    }
}
