package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.security.SecurityUtil;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.presentation.dto.response.SearchResultDto;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DocumentSearchService {

    private static final Integer ACTIVE = 1;

    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;
    private final PermissionResolver permissionResolver;

    public DocumentSearchService(VectorStore vectorStore,
                                 DocumentRepository documentRepository,
                                 PermissionResolver permissionResolver) {
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
        this.permissionResolver = permissionResolver;
    }

    public List<SearchResultDto> search(String query, int topK) {
        return toResults(similaritySearch(query, topK));
    }

    public List<Document> similaritySearch(String query, int topK) {
        return similaritySearch(query, topK, SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL);
    }

    /**
     * 유사도 threshold를 적용한 검색. 관련성 낮은 청크를 걸러
     * LLM 컨텍스트에 불필요한 토큰이 들어가는 것을 막는다.
     *
     * <p>결과는 권한 판정을 통과한 문서의 청크만 남는다. 이 메서드가 검색 · LLM 컨텍스트 ·
     * 참고 문서 목록의 공통 경로이므로, 여기서 걸러진 문서는 답변과 스니펫에도 나타나지 않는다.
     *
     * <p>현재는 벡터 필터가 작성자 기준이고 권한 판정은 그 뒤에 적용된다(post-filter).
     * 즉 결과가 좁아지기만 하며 누출되지는 않는다. 공유된 문서까지 검색되게 하려면
     * 벡터 metadata에 조상 경로를 넣는 pre-filter가 필요하고, 이는 전체 재색인을 수반한다.
     */
    public List<Document> similaritySearch(String query, int topK, double similarityThreshold) {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) throw new BusinessException(Code.UNAUTHORIZED, "인증 정보가 없습니다.");

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        List<Document> chunks = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(similarityThreshold)
                        .filterExpression(b.eq("createUser", userId).build())
                        .build()
        );

        return filterAccessible(chunks, userId);
    }

    /**
     * 색인 시점과 현재의 권한 · 위치가 다를 수 있으므로 결과를 원본 문서로 다시 확인한다.
     * 문서 단위로 판정하고 워크스페이스별 스냅샷을 재사용해, 청크 수만큼 쿼리가 나가지 않게 한다.
     */
    private List<Document> filterAccessible(List<Document> chunks, String userId) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        Set<String> documentIds = new LinkedHashSet<>();
        for (Document chunk : chunks) {
            String documentId = (String) chunk.getMetadata().get("documentId");
            if (documentId != null) {
                documentIds.add(documentId);
            }
        }
        if (documentIds.isEmpty()) {
            return List.of();
        }

        Map<String, DocumentEntity> documentsById = documentRepository
                .findByDocumentIdInAndUsable(documentIds, ACTIVE).stream()
                .collect(Collectors.toMap(DocumentEntity::getDocumentId, Function.identity()));

        Map<String, PermissionSnapshot> snapshotsByWorkspaceId = new HashMap<>();
        Map<String, Boolean> allowedByDocumentId = new HashMap<>();

        return chunks.stream()
                .filter(chunk -> {
                    String documentId = (String) chunk.getMetadata().get("documentId");
                    if (documentId == null) {
                        return false;
                    }
                    return allowedByDocumentId.computeIfAbsent(documentId, id -> {
                        DocumentEntity document = documentsById.get(id);
                        // 원본이 삭제되었는데 색인이 남아있는 경우. 불확실은 닫는 방향으로 확정한다.
                        if (document == null) {
                            return false;
                        }
                        PermissionSnapshot snapshot = snapshotsByWorkspaceId.computeIfAbsent(
                                String.valueOf(document.getWorkspaceId()),
                                key -> permissionResolver.snapshot(document.getWorkspaceId(), userId));
                        return snapshot.isAllowed(document);
                    });
                })
                .toList();
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
