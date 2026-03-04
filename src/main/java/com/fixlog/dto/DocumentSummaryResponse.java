package com.fixlog.dto;

import java.util.List;

public record DocumentSummaryResponse(
        String summary,
        List<String> tags,
        List<Double> embedding
) {
}
