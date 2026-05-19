package com.fixlog.presentation.controller.ai;

import com.fixlog.application.service.DocumentAIService;
import com.fixlog.application.service.EmbeddingService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.presentation.dto.request.DocumentRequest;
import com.fixlog.presentation.dto.response.DocumentSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai")
public class AIController {

    private final DocumentAIService documentAIService;
    private final EmbeddingService embeddingService;

    public AIController(DocumentAIService documentAIService, EmbeddingService embeddingService) {
        this.documentAIService = documentAIService;
        this.embeddingService = embeddingService;
    }

    @PostMapping("/analyze")
    public DataResponse<DocumentSummaryResponse> analyzeDocument(@Valid @RequestBody DocumentRequest request) {
        Map<String, Object> result = documentAIService.summarizeAndTag(request.content());
        String summary = (String) result.get("summary");
        List<String> tags = (List<String>) result.get("tags");
        List<Double> embedding = embeddingService.generateEmbedding(summary);
        return DataResponse.success("문서 분석이 완료되었습니다.", new DocumentSummaryResponse(summary, tags, embedding));
    }

    @PostMapping("/summarize")
    public DataResponse<String> summarizeDocument(@Valid @RequestBody DocumentRequest request) {
        return DataResponse.success("요약이 완료되었습니다.", documentAIService.summarizeDocument(request.content()));
    }

    @PostMapping("/tags")
    public DataResponse<List<String>> generateTags(@Valid @RequestBody DocumentRequest request) {
        return DataResponse.success("태그 생성이 완료되었습니다.", documentAIService.generateTags(request.content()));
    }

    @PostMapping("/embedding")
    public DataResponse<List<Double>> generateEmbedding(@Valid @RequestBody DocumentRequest request) {
        return DataResponse.success("임베딩 생성이 완료되었습니다.", embeddingService.generateEmbedding(request.content()));
    }
}
