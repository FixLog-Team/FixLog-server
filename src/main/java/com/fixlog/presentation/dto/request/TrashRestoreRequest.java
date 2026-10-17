package com.fixlog.presentation.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 휴지통 복원 요청. targetFolderId가 null이면 원래 위치(또는 루트)로 복원한다.
 * 부모 폴더가 아직 휴지통에 있을 때 targetFolderId를 지정하면 그 폴더로 이동 후 복원한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrashRestoreRequest(String targetFolderId) {

    public static TrashRestoreRequest empty() {
        return new TrashRestoreRequest(null);
    }
}
