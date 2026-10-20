package com.fixlog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "apj_image")
public class ImageEntity {

    /** 분석 실패 사유 컬럼이 길어지지 않도록 자르는 길이. */
    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @Column(name = "image_id", length = 100)
    private String imageId;

    @Column(name = "workspace_id", columnDefinition = "uuid", nullable = false)
    private UUID workspaceId;

    /**
     * 이 이미지를 처음 참조한 문서. 업로드 시점엔 문서가 저장 전이라 비어 있고 문서 저장 때 채운다.
     * 문서 복제로 같은 이미지가 여러 문서에 걸릴 수 있어 최초 연결만 기록한다.
     */
    @Column(name = "document_id", length = 100)
    private String documentId;

    @Column(name = "object_name", length = 500, nullable = false)
    private String objectName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "original_name", length = 255)
    private String originalName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "analysis_text", columnDefinition = "TEXT")
    private String analysisText;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", length = 20, nullable = false)
    private ImageAnalysisStatus analysisStatus;

    @Column(name = "analysis_error", columnDefinition = "TEXT")
    private String analysisError;

    @Column(name = "analyzed_at")
    private Instant analyzedAt;

    @Column(name = "create_user", length = 100)
    private String createUser;

    @Column(name = "create_time", updatable = false)
    private Instant createTime;

    protected ImageEntity() {
    }

    public ImageEntity(String imageId, UUID workspaceId, String objectName,
                       String contentType, String originalName, Long fileSize,
                       String contentHash, String createUser) {
        this.imageId = imageId;
        this.workspaceId = workspaceId;
        this.objectName = objectName;
        this.contentType = contentType;
        this.originalName = originalName;
        this.fileSize = fileSize;
        this.contentHash = contentHash;
        this.analysisStatus = ImageAnalysisStatus.PENDING;
        this.createUser = createUser;
        this.createTime = Instant.now();
    }

    /**
     * 문서에 연결한다. 이미 다른 문서에 묶여 있으면 그대로 둔다.
     * 복제 문서가 같은 이미지를 참조해도 최초 연결이 유지돼야 재색인 대상이 흔들리지 않는다.
     */
    public void linkToDocumentIfAbsent(String documentId) {
        if (this.documentId == null) {
            this.documentId = documentId;
        }
    }

    /**
     * 기존 연결이 끊어졌을 때 새 문서로 재연결한다.
     * 원래 문서가 삭제됐거나 이 이미지를 더 이상 참조하지 않을 때 정비 작업이 호출한다.
     */
    public void relinkToDocument(String documentId) {
        this.documentId = documentId;
    }

    public void completeAnalysis(String analysisText) {
        this.analysisText = analysisText;
        this.analysisStatus = ImageAnalysisStatus.COMPLETED;
        this.analysisError = null;
        this.analyzedAt = Instant.now();
    }

    /** 분석에 실패해도 원본과 메타데이터는 그대로 둔다. 나중에 다시 시도할 수 있다. */
    public void failAnalysis(String error) {
        this.analysisStatus = ImageAnalysisStatus.FAILED;
        this.analysisError = error == null
                ? null
                : error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH));
        this.analyzedAt = Instant.now();
    }

    /** 비전 모델이 읽을 수 없는 형식이라 분석 대상에서 제외한다. 재시도해도 결과가 같다. */
    public void skipAnalysis(String reason) {
        this.analysisStatus = ImageAnalysisStatus.SKIPPED;
        this.analysisError = reason;
        this.analyzedAt = Instant.now();
    }

    public boolean needsAnalysis() {
        return analysisStatus == ImageAnalysisStatus.PENDING;
    }

    public boolean hasSearchableText() {
        return analysisStatus == ImageAnalysisStatus.COMPLETED
                && analysisText != null && !analysisText.isBlank();
    }

    public String getImageId() { return imageId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getDocumentId() { return documentId; }
    public String getObjectName() { return objectName; }
    public String getContentType() { return contentType; }
    public String getOriginalName() { return originalName; }
    public Long getFileSize() { return fileSize; }
    public String getContentHash() { return contentHash; }
    public String getAnalysisText() { return analysisText; }
    public ImageAnalysisStatus getAnalysisStatus() { return analysisStatus; }
    public String getAnalysisError() { return analysisError; }
    public Instant getAnalyzedAt() { return analyzedAt; }
    public String getCreateUser() { return createUser; }
    public Instant getCreateTime() { return createTime; }
}
