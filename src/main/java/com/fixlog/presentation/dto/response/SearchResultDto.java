package com.fixlog.presentation.dto.response;

public record SearchResultDto(
        String documentId,
        String title,
        String folderId,
        String excerpt,
        Double score
) {}
