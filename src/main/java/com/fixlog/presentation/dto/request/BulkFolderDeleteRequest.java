package com.fixlog.presentation.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BulkFolderDeleteRequest(
        @NotEmpty(message = "폴더 ID 목록이 비어있습니다.")
        @Size(max = 50, message = "한 번에 최대 50개까지 삭제할 수 있습니다.")
        List<String> folderIds
) {}
