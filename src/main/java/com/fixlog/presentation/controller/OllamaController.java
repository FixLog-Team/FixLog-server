package com.fixlog.controller;

import com.fixlog.common.response.TypedResponse;
import com.fixlog.dto.DocumentRequest;
import com.fixlog.dto.DocumentSummaryResponse;
import com.fixlog.service.OllamaAIService;
import com.fixlog.service.OllamaEmbeddingService;
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

    /**
     * Ollama로 문서를 요약하고 태그를 생성하며, 요약본을 임베딩으로 변환합니다.
     * POST /fixlog/ollama/analyze
     */
    @PostMapping("/analyze")
    public TypedResponse<DocumentSummaryResponse> analyzeDocument(@RequestBody DocumentRequest request) {
        try {
            // 1. 문서 요약 및 태그 생성
            Map<String, Object> result = ollamaAIService.summarizeAndTag(request.getContent());
            String summary = (String) result.get("summary");
            List<String> tags = (List<String>) result.get("tags");

            // 2. 요약본을 벡터로 변환
            List<Double> embedding = ollamaEmbeddingService.generateEmbedding(summary);

            // 3. 응답 생성
            DocumentSummaryResponse response = new DocumentSummaryResponse(summary, tags, embedding);

            return new TypedResponse<DocumentSummaryResponse>() {
                {
                    setCode(com.fixlog.common.code.Code.SUCCESS);
                    setMessage("Ollama로 문서 분석이 완료되었습니다.");
                    setResult(response);
                }
            };
        } catch (Exception e) {
            return new TypedResponse<DocumentSummaryResponse>() {
                {
                    setCode(com.fixlog.common.code.Code.UNKNOWN);
                    setMessage("Ollama 문서 분석 중 오류가 발생했습니다: " + e.getMessage());
                }
            };
        }
    }

    /**
     * Ollama로 문서를 요약만 합니다.
     * POST /fixlog/ollama/summarize
     */
    @PostMapping("/summarize")
    public TypedResponse<String> summarizeDocument(@RequestBody DocumentRequest request) {
        try {
            String summary = ollamaAIService.summarizeDocument(request.getContent());

            return new TypedResponse<String>() {
                {
                    setCode(com.fixlog.common.code.Code.SUCCESS);
                    setMessage("Ollama로 요약이 완료되었습니다.");
                    setResult(summary);
                }
            };
        } catch (Exception e) {
            return new TypedResponse<String>() {
                {
                    setCode(com.fixlog.common.code.Code.UNKNOWN);
                    setMessage("Ollama 요약 중 오류가 발생했습니다: " + e.getMessage());
                }
            };
        }
    }

    /**
     * Ollama로 문서에서 태그를 생성합니다.
     * POST /fixlog/ollama/tags
     */
    @PostMapping("/tags")
    public TypedResponse<List<String>> generateTags(@RequestBody DocumentRequest request) {
        try {
            List<String> tags = ollamaAIService.generateTags(request.getContent());

            return new TypedResponse<List<String>>() {
                {
                    setCode(com.fixlog.common.code.Code.SUCCESS);
                    setMessage("Ollama로 태그 생성이 완료되었습니다.");
                    setResult(tags);
                }
            };
        } catch (Exception e) {
            return new TypedResponse<List<String>>() {
                {
                    setCode(com.fixlog.common.code.Code.UNKNOWN);
                    setMessage("Ollama 태그 생성 중 오류가 발생했습니다: " + e.getMessage());
                }
            };
        }
    }

    /**
     * Ollama로 텍스트를 임베딩 벡터로 변환합니다.
     * POST /fixlog/ollama/embedding
     */
    @PostMapping("/embedding")
    public TypedResponse<List<Double>> generateEmbedding(@RequestBody DocumentRequest request) {
        try {
            List<Double> embedding = ollamaEmbeddingService.generateEmbedding(request.getContent());

            return new TypedResponse<List<Double>>() {
                {
                    setCode(com.fixlog.common.code.Code.SUCCESS);
                    setMessage("Ollama로 임베딩 생성이 완료되었습니다.");
                    setResult(embedding);
                }
            };
        } catch (Exception e) {
            return new TypedResponse<List<Double>>() {
                {
                    setCode(com.fixlog.common.code.Code.UNKNOWN);
                    setMessage("Ollama 임베딩 생성 중 오류가 발생했습니다: " + e.getMessage());
                }
            };
        }
    }
}
