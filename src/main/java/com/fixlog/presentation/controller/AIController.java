package com.fixlog.controller;

import com.fixlog.common.response.Response;
import com.fixlog.common.response.TypedResponse;
import com.fixlog.dto.DocumentRequest;
import com.fixlog.dto.DocumentSummaryResponse;
import com.fixlog.service.DocumentAIService;
import com.fixlog.service.EmbeddingService;
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

    /**
     * 문서를 요약하고 태그를 생성하며, 요약본을 임베딩으로 변환합니다.
     * POST /fixlog/ai/analyze
     */
    @PostMapping("/analyze")
    public TypedResponse<DocumentSummaryResponse> analyzeDocument(@RequestBody DocumentRequest request) {
        try {
            // 1. 문서 요약 및 태그 생성
            Map<String, Object> result = documentAIService.summarizeAndTag(request.content());
            String summary = (String) result.get("summary");
            List<String> tags = (List<String>) result.get("tags");

            // 2. 요약본을 벡터로 변환
            List<Double> embedding = embeddingService.generateEmbedding(summary);

            // 3. 응답 생성
            DocumentSummaryResponse response = new DocumentSummaryResponse(summary, tags, embedding);

            return new TypedResponse<DocumentSummaryResponse>() {
                {
                    setCode(com.fixlog.common.code.Code.SUCCESS);
                    setMessage("문서 분석이 완료되었습니다.");
                    setResult(response);
                }
            };
        } catch (Exception e) {
            return new TypedResponse<DocumentSummaryResponse>() {
                {
                    setCode(com.fixlog.common.code.Code.UNKNOWN);
                    setMessage("문서 분석 중 오류가 발생했습니다: " + e.getMessage());
                }
            };
        }
    }

    /**
     * 문서를 요약만 합니다.
     * POST /fixlog/ai/summarize
     */
    @PostMapping("/summarize")
    public TypedResponse<String> summarizeDocument(@RequestBody DocumentRequest request) {
        try {
            String summary = documentAIService.summarizeDocument(request.content());

            return new TypedResponse<String>() {
                {
                    setCode(com.fixlog.common.code.Code.SUCCESS);
                    setMessage("요약이 완료되었습니다.");
                    setResult(summary);
                }
            };
        } catch (Exception e) {
            return new TypedResponse<String>() {
                {
                    setCode(com.fixlog.common.code.Code.UNKNOWN);
                    setMessage("요약 중 오류가 발생했습니다: " + e.getMessage());
                }
            };
        }
    }

    /**
     * 문서에서 태그를 생성합니다.
     * POST /fixlog/ai/tags
     */
    @PostMapping("/tags")
    public TypedResponse<List<String>> generateTags(@RequestBody DocumentRequest request) {
        try {
            List<String> tags = documentAIService.generateTags(request.content());

            return new TypedResponse<List<String>>() {
                {
                    setCode(com.fixlog.common.code.Code.SUCCESS);
                    setMessage("태그 생성이 완료되었습니다.");
                    setResult(tags);
                }
            };
        } catch (Exception e) {
            return new TypedResponse<List<String>>() {
                {
                    setCode(com.fixlog.common.code.Code.UNKNOWN);
                    setMessage("태그 생성 중 오류가 발생했습니다: " + e.getMessage());
                }
            };
        }
    }

    /**
     * 텍스트를 임베딩 벡터로 변환합니다.
     * POST /fixlog/ai/embedding
     */
    @PostMapping("/embedding")
    public TypedResponse<List<Double>> generateEmbedding(@RequestBody DocumentRequest request) {
        try {
            List<Double> embedding = embeddingService.generateEmbedding(request.content());

            return new TypedResponse<List<Double>>() {
                {
                    setCode(com.fixlog.common.code.Code.SUCCESS);
                    setMessage("임베딩 생성이 완료되었습니다.");
                    setResult(embedding);
                }
            };
        } catch (Exception e) {
            return new TypedResponse<List<Double>>() {
                {
                    setCode(com.fixlog.common.code.Code.UNKNOWN);
                    setMessage("임베딩 생성 중 오류가 발생했습니다: " + e.getMessage());
                }
            };
        }
    }
}
