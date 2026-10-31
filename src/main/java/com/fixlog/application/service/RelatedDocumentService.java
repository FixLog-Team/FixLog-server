package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.ResourceType;
import com.fixlog.presentation.dto.response.RelatedDocumentDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 연관 문서.
 *
 * <p>질의 문장을 새로 임베딩하지 않는다. 문서의 청크 벡터가 이미 색인돼 있으므로, 그 벡터들의
 * 이웃을 찾는다. 문서 조회는 가장 빈번한 동작이라 거기에 임베딩 호출을 얹지 않으려는 선택이다.
 *
 * <p>문서 대표 벡터(청크 평균) 대신 <b>청크별 최고 점수</b>로 판정한다. 트러블슈팅 문서는 증상·원인·해결이
 * 한 문서에 섞여 있어 평균을 내면 특징이 흐려진다. "한 대목이라도 강하게 겹치면 연관"이 더 맞다.
 *
 * <p>권한은 검색과 같은 규칙으로 <b>사전 필터</b>를 건다. 뒤에서 걸러내면 공유되지 않은 문서의
 * 제목·발췌가 이미 나간 뒤가 된다.
 */
@Service
public class RelatedDocumentService {

    /**
     * 소스 문서의 청크마다 이웃을 따로 찾는다(LATERAL). 한 번에 조인하면 벡터 인덱스를 타지 못해
     * 전체 스캔이 된다. 청크별로 나눠 쏘면 각 질의가 HNSW 인덱스를 탄다.
     *
     * <p>{@code %s}는 벡터 테이블 이름이다. 설정에서 오는 상수이며 사용자 입력이 아니다.
     */
    private static final String NEIGHBOR_SQL_TEMPLATE = """
            SELECT n.document_id, n.title, n.folder_id, n.content, n.score
            FROM (
                SELECT d.embedding
                FROM %s d
                WHERE d.metadata->>'documentId' = :documentId
                ORDER BY (d.metadata->>'chunkIndex')::int NULLS LAST, d.id
                LIMIT :sourceChunkLimit
            ) s
            CROSS JOIN LATERAL (
                SELECT t.metadata->>'documentId' AS document_id,
                       t.metadata->>'title'      AS title,
                       t.metadata->>'folderId'   AS folder_id,
                       t.content                 AS content,
                       1 - (t.embedding <=> s.embedding) AS score
                FROM %s t
                WHERE t.metadata->>'workspaceId' = :workspaceId
                  AND t.metadata->>'documentId' <> :documentId
                  AND t.metadata->>'documentId' IN (:accessibleIds)
                ORDER BY t.embedding <=> s.embedding
                LIMIT :neighborsPerChunk
            ) n
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final String neighborSql;
    private final DocumentRepository documentRepository;
    private final WorkspaceContext workspaceContext;
    private final PermissionEvaluator permissionEvaluator;
    private final double similarityThreshold;
    private final int sourceChunkLimit;
    private final int neighborsPerChunk;
    private final int excerptLength;

    public RelatedDocumentService(@Qualifier("pgVectorJdbcTemplate") JdbcTemplate pgVectorJdbcTemplate,
                                  DocumentRepository documentRepository,
                                  WorkspaceContext workspaceContext,
                                  PermissionEvaluator permissionEvaluator,
                                  @Value("${fixlog.related.similarity-threshold}") double similarityThreshold,
                                  @Value("${fixlog.related.source-chunk-limit}") int sourceChunkLimit,
                                  @Value("${fixlog.related.neighbors-per-chunk}") int neighborsPerChunk,
                                  @Value("${fixlog.related.excerpt-length}") int excerptLength,
                                  @Value("${fixlog.vectorstore.table-name:document_embeddings}") String vectorTableName) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(pgVectorJdbcTemplate);
        this.neighborSql = NEIGHBOR_SQL_TEMPLATE.formatted(vectorTableName, vectorTableName);
        this.documentRepository = documentRepository;
        this.workspaceContext = workspaceContext;
        this.permissionEvaluator = permissionEvaluator;
        this.similarityThreshold = similarityThreshold;
        this.sourceChunkLimit = sourceChunkLimit;
        this.neighborsPerChunk = neighborsPerChunk;
        this.excerptLength = excerptLength;
    }

    public List<RelatedDocumentDto> findRelated(String documentId, int limit) {
        UUID workspaceId = workspaceContext.requireCurrentWorkspaceId();
        requireViewable(documentId);

        List<String> accessible = accessibleDocumentIds(workspaceId, documentId);
        // 볼 수 있는 다른 문서가 없으면 벡터 조회 자체를 돌리지 않는다
        if (accessible.isEmpty()) {
            return List.of();
        }

        return merge(queryNeighbors(documentId, workspaceId.toString(), accessible), limit);
    }

    /** 벡터 이웃 조회. pgvector 전용이라 H2에서는 돌지 않는다. */
    List<NeighborChunk> queryNeighbors(String documentId, String workspaceId, List<String> accessibleIds) {
        return jdbcTemplate.query(
                neighborSql,
                Map.of("documentId", documentId,
                        "workspaceId", workspaceId,
                        "accessibleIds", accessibleIds,
                        "sourceChunkLimit", sourceChunkLimit,
                        "neighborsPerChunk", neighborsPerChunk),
                (rs, rowNum) -> new NeighborChunk(
                        rs.getString("document_id"),
                        rs.getString("title"),
                        rs.getString("folder_id"),
                        rs.getString("content"),
                        rs.getDouble("score")));
    }

    /**
     * 청크 단위 이웃을 문서 단위로 합친다. 같은 문서의 청크가 여러 번 걸리므로 가장 높은 점수만 남기고,
     * 그때의 대목을 발췌로 쓴다.
     *
     * <p>임계값에 못 미치면 버린다. 연관 없는 문서를 끼워 넣느니 비어 보이는 편이 낫다 —
     * 한 번 엉뚱한 결과를 보면 목록 전체를 믿지 않게 된다.
     */
    List<RelatedDocumentDto> merge(List<NeighborChunk> neighbors, int limit) {
        Map<String, NeighborChunk> best = new LinkedHashMap<>();
        for (NeighborChunk chunk : neighbors) {
            if (chunk.score() < similarityThreshold) continue;
            best.merge(chunk.documentId(), chunk,
                    (existing, candidate) -> candidate.score() > existing.score() ? candidate : existing);
        }

        return best.values().stream()
                .sorted(Comparator.comparingDouble(NeighborChunk::score).reversed())
                .limit(limit)
                .map(chunk -> new RelatedDocumentDto(
                        chunk.documentId(),
                        chunk.title(),
                        chunk.folderId(),
                        truncate(chunk.content()),
                        chunk.score()))
                .toList();
    }

    /** 연관 목록을 보려면 그 문서를 볼 수 있어야 한다. */
    private void requireViewable(String documentId) {
        // 문서 열람 자체는 상세 조회에서 이미 감사 로그에 남는다. 여기서 또 남기면 조회 한 번이 두 줄이 된다.
        if (!permissionEvaluator.evaluate(ResourceType.DOCUMENT, documentId).allowed()) {
            throw new BusinessException(Code.FORBIDDEN, "이 문서를 볼 권한이 없습니다.");
        }
    }

    private List<String> accessibleDocumentIds(UUID workspaceId, String excludedDocumentId) {
        PermissionEvaluator.Scope scope = permissionEvaluator.scopeFor(workspaceId);
        return documentRepository
                .findByWorkspaceIdAndUsableOrderByOrdinalAscCreateTimeAsc(workspaceId, Integer.valueOf(1))
                .stream()
                .filter(doc -> !doc.getDocumentId().equals(excludedDocumentId))
                .filter(doc -> scope.canViewDocument(doc.getDocumentId(), doc.getFolderId()))
                .map(DocumentEntity::getDocumentId)
                .toList();
    }

    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > excerptLength ? text.substring(0, excerptLength) + "..." : text;
    }

    /** 이웃 청크 한 건. 문서 단위로 합치기 전의 원재료다. */
    record NeighborChunk(String documentId, String title, String folderId, String content, double score) {
    }
}
