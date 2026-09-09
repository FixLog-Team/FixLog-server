package com.fixlog.presentation.dto.request;

import com.fixlog.domain.model.PermissionType;
import jakarta.validation.constraints.NotNull;

/**
 * 워크스페이스 기본 접근 정책 변경.
 *
 * <p>{@code ALLOW}면 구성원이 별도 설정 없이 접근할 수 있고,
 * {@code DENY}면 명시적으로 부여한 것만 열린다.
 */
public record WorkspaceBaseAccessRequest(
        @NotNull PermissionType baseAccess
) {}
