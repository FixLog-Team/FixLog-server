package com.fixlog.presentation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SemanticSearchRequest(
        @NotBlank String query,
        @Min(1) @Max(20) Integer topK
) {
    public SemanticSearchRequest {
        if (topK == null) topK = 5;
    }
}
