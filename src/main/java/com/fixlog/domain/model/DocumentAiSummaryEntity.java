package com.fixlog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 문서 AI 요약 결과 캐시.
 * 문서의 contentHash가 변하지 않는 한 저장된 요약을 재사용하여 LLM 재호출을 방지한다.
 * AI Artifact는 언제든 재생성 가능하며 원본 문서와 분리 보관한다.
 */
@Entity
@Table(name = "apj_document_ai_summary")
public class DocumentAiSummaryEntity {

    @Id
    @Column(name = "document_id", length = 100)
    private String documentId;

    @Column(name = "content_hash", length = 64, nullable = false)
    private String contentHash;

    @Column(name = "summary", columnDefinition = "TEXT", nullable = false)
    private String summary;

    @Column(name = "create_time", updatable = false)
    private Instant createTime;

    @Column(name = "update_time")
    private Instant updateTime;

    protected DocumentAiSummaryEntity() {
    }

    public DocumentAiSummaryEntity(String documentId, String contentHash, String summary) {
        this.documentId = documentId;
        this.contentHash = contentHash;
        this.summary = summary;
        this.createTime = Instant.now();
        this.updateTime = Instant.now();
    }

    public void updateSummary(String contentHash, String summary) {
        this.contentHash = contentHash;
        this.summary = summary;
        this.updateTime = Instant.now();
    }

    public boolean matches(String contentHash) {
        return this.contentHash != null && this.contentHash.equals(contentHash);
    }

    public String getDocumentId() { return documentId; }
    public String getContentHash() { return contentHash; }
    public String getSummary() { return summary; }
    public Instant getCreateTime() { return createTime; }
    public Instant getUpdateTime() { return updateTime; }
}
