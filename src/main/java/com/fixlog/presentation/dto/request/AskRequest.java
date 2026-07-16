package com.fixlog.presentation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskRequest(
        @NotBlank(message = "question은 필수입니다.")
        @Size(max = 2000, message = "question은 2000자를 초과할 수 없습니다.")
        String question,
        @Min(1) @Max(20) Integer topK
) {
    public AskRequest {
        if (topK == null) topK = 5;
    }
}
