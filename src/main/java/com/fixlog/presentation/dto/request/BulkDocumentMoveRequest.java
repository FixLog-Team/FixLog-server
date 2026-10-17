package com.fixlog.presentation.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BulkDocumentMoveRequest(
        @NotEmpty(message = "문서 ID 목록이 비어있습니다.")
        @Size(max = 100, message = "한 번에 최대 100개까지 이동할 수 있습니다.")
        List<String> documentIds,
        String targetFolderId
) {}
