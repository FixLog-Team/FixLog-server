package com.fixlog.presentation.controller.ai;

import com.fixlog.application.service.OllamaAIService;
import com.fixlog.application.service.OllamaEmbeddingService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.presentation.dto.request.DocumentRequest;
import com.fixlog.presentation.dto.response.DocumentSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ollama")
public class OllamaController {

    private final OllamaAIService ollamaAIService;
    private final OllamaEmbeddingService ollamaEmbeddingService;

    public OllamaController(OllamaAIService ollamaAIService, OllamaEmbeddingService ollamaEmbeddingService) {
        this.ollamaAIService = ollamaAIService;
        this.ollamaEmbeddingService = ollamaEmbeddingService;
    }

    @PostMapping("/analyze")
    public DataResponse<DocumentSummaryResponse> analyzeDocument(@Valid @RequestBody DocumentRequest request) {
        Map<String, Object> result = ollamaAIService.summarizeAndTag(request.content());
        String summary = (String) result.get("summary");
        List<String> tags = (List<String>) result.get("tags");
        List<Double> embedding = ollamaEmbeddingService.generateEmbedding(summary);
        return DataResponse.success("Ollama로 문서 분석이 완료되었습니다.", new DocumentSummaryResponse(summary, tags, embedding));
    }

    @PostMapping("/summarize")
    public DataResponse<String> summarizeDocument(@Valid @RequestBody DocumentRequest request) {
        return DataResponse.success("Ollama로 요약이 완료되었습니다.", ollamaAIService.summarizeDocument(request.content()));
    }

    @PostMapping("/tags")
    public DataResponse<List<String>> generateTags(@Valid @RequestBody DocumentRequest request) {
        return DataResponse.success("Ollama로 태그 생성이 완료되었습니다.", ollamaAIService.generateTags(request.content()));
    }

    @PostMapping("/embedding")
    public DataResponse<List<Double>> generateEmbedding(@Valid @RequestBody DocumentRequest request) {
        return DataResponse.success("Ollama로 임베딩 생성이 완료되었습니다.", ollamaEmbeddingService.generateEmbedding(request.content()));
    }
}
