package com.fixlog.presentation.dto.response;

import java.util.List;

public record FolderContentsDto(
        List<FolderDto> folders,
        List<DocumentDto> documents
) {
}
