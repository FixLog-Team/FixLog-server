package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.presentation.dto.response.DocumentDto;
import com.fixlog.presentation.dto.response.FolderDto;
import com.fixlog.presentation.dto.response.SearchResultDto;
import com.fixlog.presentation.dto.response.TitleSearchResultDto;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
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
    private final FolderRepository folderRepository;
    private final WorkspaceContext workspaceContext;
    private final PermissionEvaluator permissionEvaluator;

    public DocumentSearchService(VectorStore vectorStore,
                                 DocumentRepository documentRepository,
                                 FolderRepository folderRepository,
                                 WorkspaceContext workspaceContext,
                                 PermissionEvaluator permissionEvaluator) {
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
        this.folderRepository = folderRepository;
        this.workspaceContext = workspaceContext;
        this.permissionEvaluator = permissionEvaluator;
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
     */
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
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(similarityThreshold)
                        .filterExpression(b.and(
                                b.eq("workspaceId", workspaceId.toString()),
                                b.in("documentId", accessible.toArray())).build())
                        .build()
        );
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

    /**
     * 제목 기반 통합 검색. folderId가 있으면 해당 폴더의 서브트리(하위 폴더·문서 전체)로 범위를 제한한다.
     */
    public TitleSearchResultDto titleSearch(String q, String folderId) {
        UUID workspaceId = workspaceContext.requireCurrentWorkspaceId();
        PermissionEvaluator.Scope scope = permissionEvaluator.scopeFor(workspaceId);
        String keyword = q == null ? "" : q.toLowerCase();

        List<FolderEntity> candidateFolders;
        List<DocumentEntity> candidateDocuments;

        if (folderId != null) {
            FolderEntity root = folderRepository.findByFolderIdAndUsable(folderId, Integer.valueOf(1))
                    .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));
            // path prefix로 서브트리 전체(루트 포함) 조회
            candidateFolders = folderRepository.findByPathStartingWith(root.getPath());
            Set<String> subtreeFolderIds = candidateFolders.stream()
                    .map(FolderEntity::getFolderId)
                    .collect(Collectors.toSet());
            candidateDocuments = documentRepository
                    .findByWorkspaceIdAndUsableOrderByOrdinalAscCreateTimeAsc(workspaceId, Integer.valueOf(1))
                    .stream()
                    .filter(d -> subtreeFolderIds.contains(d.getFolderId()))
                    .toList();
        } else {
            candidateFolders = folderRepository.findByWorkspaceIdAndUsable(workspaceId, Integer.valueOf(1));
            candidateDocuments = documentRepository
                    .findByWorkspaceIdAndUsableOrderByOrdinalAscCreateTimeAsc(workspaceId, Integer.valueOf(1));
        }

        List<FolderDto> folders = candidateFolders.stream()
                .filter(f -> scope.canViewFolder(f.getFolderId()))
                .filter(f -> f.getFolderName() != null && f.getFolderName().toLowerCase().contains(keyword))
                .map(FolderDto::from)
                .toList();

        List<DocumentDto> documents = candidateDocuments.stream()
                .filter(d -> scope.canViewDocument(d.getDocumentId(), d.getFolderId()))
                .filter(d -> d.getTitle() != null && d.getTitle().toLowerCase().contains(keyword))
                .map(DocumentDto::from)
                .toList();

        return new TitleSearchResultDto(folders, documents);
    }
}
