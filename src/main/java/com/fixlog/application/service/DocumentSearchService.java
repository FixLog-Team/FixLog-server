package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.presentation.dto.response.SearchResultDto;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 의미 기반 검색.
 *
 * <p>권한은 <b>사전 필터</b>로 건다. 상위 N개를 먼저 뽑고 걸러내면 걸러진 만큼 결과가 비어
 * 사용자에게는 "검색이 안 되는" 것으로 보이기 때문이다 (FR-SHR-006).
 */
@Service
public class DocumentSearchService {

    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;
    private final WorkspaceContext workspaceContext;
    private final PermissionEvaluator permissionEvaluator;
    private final int candidateMultiplier;
    private final int maxChunksPerDocument;

    public DocumentSearchService(
            VectorStore vectorStore,
            DocumentRepository documentRepository,
            WorkspaceContext workspaceContext,
            PermissionEvaluator permissionEvaluator,
            @Value("${fixlog.ai.search.candidate-multiplier:4}") int candidateMultiplier,
            @Value("${fixlog.ai.search.max-chunks-per-document:2}") int maxChunksPerDocument) {
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
        this.workspaceContext = workspaceContext;
        this.permissionEvaluator = permissionEvaluator;
        this.candidateMultiplier = Math.max(1, candidateMultiplier);
        this.maxChunksPerDocument = Math.max(1, maxChunksPerDocument);
    }

    public List<SearchResultDto> search(String query, int topK) {
        return toResults(similaritySearch(query, topK));
    }

    public List<Document> similaritySearch(String query, int topK) {
        return similaritySearch(query, topK, SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL);
    }

    /** 현재 워크스페이스에서 조회 권한이 있는 문서만 대상으로 유사도 검색한다. */
    public List<Document> similaritySearch(String query, int topK, double similarityThreshold) {
        UUID workspaceId = workspaceContext.requireCurrentWorkspaceId();
        PermissionEvaluator.Scope scope = permissionEvaluator.scopeFor(workspaceId);

        List<String> accessible = documentRepository
                .findByWorkspaceIdAndUsableOrderByOrdinalAscCreateTimeAsc(workspaceId, Integer.valueOf(1))
                .stream()
                .filter(doc -> scope.canViewDocument(doc.getDocumentId(), doc.getFolderId()))
                .map(DocumentEntity::getDocumentId)
                .toList();

        // 볼 수 있는 문서가 없으면 벡터 조회 자체를 돌리지 않는다
        if (accessible.isEmpty()) {
            return List.of();
        }
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        int candidateCount = Math.max(topK, topK * candidateMultiplier);
        List<Document> candidates = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(candidateCount)
                        .similarityThreshold(similarityThreshold)
                        .filterExpression(b.and(
                                b.eq("workspaceId", workspaceId.toString()),
                                b.in("documentId", accessible.toArray())).build())
                        .build()
        );
        return limitChunksPerDocument(candidates, topK);
    }

    List<Document> limitChunksPerDocument(List<Document> candidates, int topK) {
        Map<String, Integer> documentCounts = new HashMap<>();
        return candidates.stream()
                .filter(document -> hasCapacity(document, documentCounts))
                .limit(topK)
                .toList();
    }

    private boolean hasCapacity(Document document, Map<String, Integer> documentCounts) {
        String documentId = String.valueOf(document.getMetadata().get("documentId"));
        int currentCount = documentCounts.getOrDefault(documentId, 0);
        if (currentCount >= maxChunksPerDocument) return false;

        documentCounts.put(documentId, currentCount + 1);
        return true;
    }

    public List<SearchResultDto> toResults(List<Document> documents) {
        return documents.stream()
                .map(doc -> new SearchResultDto(
                        (String) doc.getMetadata().get("documentId"),
                        (String) doc.getMetadata().get("title"),
                        (String) doc.getMetadata().get("folderId"),
                        truncate(doc.getText(), 200),
                        doc.getScore()
                ))
                .toList();
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }
}
