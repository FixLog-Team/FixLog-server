package com.fixlog.presentation.dto.request;

import com.fixlog.domain.model.PermissionLevel;
import com.fixlog.domain.model.PrincipalType;

import java.util.UUID;

/**
 * 공유 요청. 사용자는 이메일로도 지정할 수 있다 — 초대와 같은 방식으로 대상을 고르기 위함이다.
 * {@code principalId}와 {@code email}이 함께 오면 {@code principalId}가 우선한다.
 */
public record ShareRequest(
        PrincipalType principalType,
        UUID principalId,
        String email,
        PermissionLevel level,
        Boolean canDownload
) {
    public boolean allowsDownload() {
        return canDownload == null || canDownload;
    }
}
