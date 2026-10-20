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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    private static final Logger log = LoggerFactory.getLogger(DocumentSearchService.class);

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

        log.info("벡터 검색 시작: workspaceId={}, query='{}', topK={}, threshold={}, accessible={}",
                workspaceId, query, topK, similarityThreshold, accessible);

        // 볼 수 있는 문서가 없으면 벡터 조회 자체를 돌리지 않는다
        if (accessible.isEmpty()) {
            log.warn("벡터 검색 스킵: 접근 가능 문서 없음");
            return List.of();
        }

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(similarityThreshold)
                        .filterExpression(b.and(
                                b.eq("workspaceId", workspaceId.toString()),
                                b.in("documentId", accessible.toArray())).build())
                        .build()
        );
        log.info("벡터 검색 결과: {}건", results.size());
        return results;
    }

    /**
     * 청크 목록을 문서 단위로 합쳐 반환한다.
     * 같은 문서의 텍스트·이미지 청크가 따로 나가지 않도록 documentId 기준으로 병합한다.
     * 점수는 가장 높은 청크의 값을 쓰고, 이미지는 images 목록에 모은다.
     */
    public List<SearchResultDto> toResults(List<Document> documents) {
        // LinkedHashMap으로 검색 점수 순서(벡터 DB가 돌려준 순서)를 유지한다
        Map<String, MergeAccumulator> accumulators = new LinkedHashMap<>();

        for (Document doc : documents) {
            String documentId = (String) doc.getMetadata().get("documentId");
            String source = (String) doc.getMetadata().getOrDefault("source", "text");
            String excerpt = truncate(doc.getText(), 200);

            MergeAccumulator acc = accumulators.get(documentId);
            if (acc == null) {
                acc = new MergeAccumulator(
                        documentId,
                        (String) doc.getMetadata().get("title"),
                        (String) doc.getMetadata().get("folderId"));
                accumulators.put(documentId, acc);
            }

            acc.addChunk(source, excerpt, doc.getScore(),
                    (String) doc.getMetadata().get("imageId"),
                    (String) doc.getMetadata().get("imageName"));
        }

        return accumulators.values().stream().map(MergeAccumulator::toDto).toList();
    }

    private static class MergeAccumulator {
        private final String documentId;
        private final String title;
        private final String folderId;
        private final StringBuilder excerptBuilder = new StringBuilder();
        private double bestScore;
        private boolean hasText;
        private boolean hasImage;
        private final List<SearchResultDto.ImageRef> images = new ArrayList<>();

        MergeAccumulator(String documentId, String title, String folderId) {
            this.documentId = documentId;
            this.title = title;
            this.folderId = folderId;
        }

        void addChunk(String source, String excerpt, Double score,
                      String imageId, String imageName) {
            if (score != null && score > bestScore) bestScore = score;
            if (!excerptBuilder.isEmpty()) excerptBuilder.append(" … ");
            excerptBuilder.append(excerpt);

            if ("image".equals(source) && imageId != null) {
                hasImage = true;
                images.add(new SearchResultDto.ImageRef(imageId, imageName));
            } else {
                hasText = true;
            }
        }

        SearchResultDto toDto() {
            String source = (hasText && hasImage) ? "mixed" : hasImage ? "image" : "text";
            String firstImageId = images.isEmpty() ? null : images.get(0).imageId();
            String firstImageName = images.isEmpty() ? null : images.get(0).imageName();
            return new SearchResultDto(
                    documentId, title, folderId,
                    truncateStatic(excerptBuilder.toString(), 400),
                    bestScore, source,
                    firstImageId, firstImageName,
                    List.copyOf(images));
        }
    }

    private static String truncateStatic(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "..." : text;
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
