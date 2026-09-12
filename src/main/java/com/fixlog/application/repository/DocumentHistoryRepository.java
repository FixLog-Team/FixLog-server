package com.fixlog.application.repository;

import com.fixlog.domain.model.DocumentHistoryEntity;
import com.fixlog.domain.model.DocumentHistorySource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DocumentHistoryRepository extends JpaRepository<DocumentHistoryEntity, String> {

    /** 히스토리 목록. blocks 를 제외한 메타데이터만 조회한다(버전마다 본문 전문이 딸려오지 않도록). */
    Page<DocumentHistorySummary> findByDocumentIdOrderByCreateTimeDesc(String documentId, Pageable pageable);

    /** historyId 가 해당 문서 소속인지까지 확인해 다른 문서의 버전 조회를 차단한다. */
    Optional<DocumentHistoryEntity> findByHistoryIdAndDocumentId(String historyId, String documentId);

    long countByDocumentId(String documentId);

    @Query("""
            select h.historyId from DocumentHistoryEntity h
            where h.documentId = :documentId
            order by h.createTime desc
            """)
    List<String> findHistoryIdsNewestFirst(@Param("documentId") String documentId);

    void deleteByDocumentId(String documentId);

    /** 목록 조회 전용 프로젝션 */
    interface DocumentHistorySummary {
        String getHistoryId();

        String getTitle();

        DocumentHistorySource getSource();

        String getCreateUser();

        Instant getCreateTime();
    }
}
