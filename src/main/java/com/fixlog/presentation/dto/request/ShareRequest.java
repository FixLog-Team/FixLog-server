package com.fixlog.presentation.dto.request;

import com.fixlog.domain.model.PermissionType;
import com.fixlog.domain.model.PrincipalType;

import java.util.UUID;

/**
 * 권한 부여 요청. 사용자는 이메일로도 지정할 수 있다.
 * {@code principalId}와 {@code email}이 함께 오면 {@code principalId}가 우선한다.
 */
public record ShareRequest(
        PrincipalType principalType,
        UUID principalId,
        String email,
        PermissionType permissionType,
        Boolean canDownload
) {
    public boolean allowsDownload() {
        return canDownload == null || canDownload;
    }

    public PermissionType resolvedPermissionType() {
        return permissionType == null ? PermissionType.ALLOW : permissionType;
    }
}
