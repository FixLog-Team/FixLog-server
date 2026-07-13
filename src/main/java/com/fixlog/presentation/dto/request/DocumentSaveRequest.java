package com.fixlog.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record DocumentSaveRequest(
        @NotBlank String title,
        @NotNull JsonNode blocks
) {
}
