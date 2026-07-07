package com.fixlog.presentation.dto.request;

public record DocumentCreateRequest(
        String folderId,
        String title
) {
}
