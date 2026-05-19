package com.fixlog.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DocumentTitleRequest(
        @NotBlank String title
) {
}
