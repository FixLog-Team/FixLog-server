package com.fixlog.presentation.controller.search;

import com.fixlog.application.service.DocumentSearchService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.presentation.dto.request.SemanticSearchRequest;
import com.fixlog.presentation.dto.response.SearchResultDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/search")
@Tag(name = "Search")
public class SearchController {

    private final DocumentSearchService searchService;

    public SearchController(DocumentSearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping
    @Operation(operationId = "search")
    public DataResponse<List<SearchResultDto>> search(@Valid @RequestBody SemanticSearchRequest request) {
        return DataResponse.success("검색이 완료되었습니다.",
                searchService.search(request.query(), request.topK()));
    }
}
