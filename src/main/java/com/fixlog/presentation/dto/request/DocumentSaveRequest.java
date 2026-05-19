package com.fixlog.presentation.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DocumentSaveRequest(
        @NotBlank String title,
        @NotNull JsonNode blocks
) {
}
