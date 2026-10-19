package com.fixlog.presentation.dto.response;

import java.util.List;

public record TitleSearchResultDto(
        List<FolderDto> folders,
        List<DocumentDto> documents
) {
}
