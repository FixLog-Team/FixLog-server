package com.fixlog.presentation.dto.response;

import java.util.List;

public record SharedWithMeDto(
        List<FolderDto> folders,
        List<DocumentDto> documents
) {
}
