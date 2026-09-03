package com.fixlog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 문서의 과거 버전 스냅샷.
 * 문서를 저장할 때 덮어쓰기 직전의 내용을 이 테이블로 밀어넣으므로,
 * 현재 본문은 항상 apj_document 에만 존재하고 여기에는 지나간 버전만 쌓인다.
 */
@Entity
@Table(name = "apj_document_history",
        indexes = @Index(name = "idx_document_history_document", columnList = "document_id, create_time"))
public class DocumentHistoryEntity {

    @Id
    @Column(name = "history_id", length = 100)
    private String historyId;

    @Column(name = "document_id", length = 100, nullable = false)
    private String documentId;

    /** 스냅샷 시점의 제목 */
    @Column(name = "title", length = 255, nullable = false)
    private String title;

    /** 스냅샷 시점의 블록 JSON 전문 */
    @Column(name = "blocks", columnDefinition = "TEXT")
    private String blocks;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20, nullable = false)
    private DocumentHistorySource source;

    /** 이 버전을 마지막으로 편집한 사용자 (스냅샷 대상 문서의 updateUser) */
    @Column(name = "create_user", length = 100)
    private String createUser;

    @Column(name = "create_time", updatable = false)
    private Instant createTime;

    protected DocumentHistoryEntity() {
    }

    public DocumentHistoryEntity(String historyId, String documentId, String title, String blocks,
                                 String contentHash, DocumentHistorySource source, String createUser) {
        this.historyId = historyId;
        this.documentId = documentId;
        this.title = title;
        this.blocks = blocks;
        this.contentHash = contentHash;
        this.source = source;
        this.createUser = createUser;
        this.createTime = Instant.now();
    }

    public String getHistoryId() { return historyId; }
    public String getDocumentId() { return documentId; }
    public String getTitle() { return title; }
    public String getBlocks() { return blocks; }
    public String getContentHash() { return contentHash; }
    public DocumentHistorySource getSource() { return source; }
    public String getCreateUser() { return createUser; }
    public Instant getCreateTime() { return createTime; }
}
