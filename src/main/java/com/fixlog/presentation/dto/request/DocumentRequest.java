package com.fixlog.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentRequest(
        @NotBlank(message = "content는 필수입니다.")
        @Size(max = 50000, message = "content는 50000자를 초과할 수 없습니다.")
        String content
) {}
