package com.fixlog.presentation.dto.request;

public record FolderRequest(
        String workspaceId,
        String parentId,
        String folderName,
        Integer ordinal
) {}