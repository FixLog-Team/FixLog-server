package com.fixlog.presentation.dto.request;

public record FolderRequest(
        String parentId,
        String folderName,
        Integer ordinal
) {}