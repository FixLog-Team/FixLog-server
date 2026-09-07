package com.fixlog.presentation.dto.request;

import com.fixlog.domain.model.BaseAccess;
import jakarta.validation.constraints.NotNull;

/**
 * 노드의 기본 접근 정책 변경. INHERIT은 상속 재개를 뜻한다.
 * 단, 워크스페이스 루트는 상속할 부모가 없으므로 INHERIT을 받을 수 없다.
 */
public record BaseAccessRequest(
        @NotNull BaseAccess baseAccess
) {}
