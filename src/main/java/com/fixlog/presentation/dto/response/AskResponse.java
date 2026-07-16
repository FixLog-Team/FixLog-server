package com.fixlog.presentation.dto.response;

import java.util.List;

public record AskResponse(String answer, List<SearchResultDto> references) {}
