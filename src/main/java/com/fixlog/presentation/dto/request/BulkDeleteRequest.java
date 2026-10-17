package com.fixlog.presentation.dto.request;

import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkDeleteRequest(
        @Size(max = 100, message = "문서는 한 번에 최대 100개까지 삭제할 수 있습니다.")
        List<String> documentIds,

        @Size(max = 50, message = "폴더는 한 번에 최대 50개까지 삭제할 수 있습니다.")
        List<String> folderIds
) {}
