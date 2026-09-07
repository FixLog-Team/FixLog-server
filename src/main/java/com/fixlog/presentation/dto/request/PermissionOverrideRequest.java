package com.fixlog.presentation.dto.request;

import com.fixlog.domain.model.AccessEffect;
import jakarta.validation.constraints.NotNull;

/** 특정 사용자에게 이 노드의 접근 여부를 직접 지정한다. */
public record PermissionOverrideRequest(
        @NotNull AccessEffect effect
) {}
