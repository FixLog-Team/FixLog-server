package com.fixlog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * 문서 본문의 시점 스냅샷.
 *
 * <p><b>불변이다.</b> 수정 메서드를 두지 않으며 롤백조차 기존 리비전을 건드리지 않고
 * 새 리비전을 쌓는다. 되돌린 사실 자체도 기록으로 남아야 하기 때문이다 (FR-REV-003, 006).
 */
@Entity
@Table(
        name = "document_revision",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_document_revision_no", columnNames = {"document_id", "revision_no"})
)
public class DocumentRevisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "document_id", length = 100, nullable = false)
    private String documentId;

    @Column(name = "workspace_id", columnDefinition = "uuid", nullable = false)
    private UUID workspaceId;

    /** 문서 안에서 1부터 증가한다. 화면에 "3번째 판"처럼 보여주기 위한 값이다. */
    @Column(name = "revision_no", nullable = false)
    private int revisionNo;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "blocks", columnDefinition = "TEXT")
    private String blocks;

    @Column(name = "plain_text", columnDefinition = "TEXT")
    private String plainText;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /** 어느 리비전에서 되돌린 결과인지. 일반 저장이면 null. */
    @Column(name = "restored_from_no")
    private Integer restoredFromNo;

    @Column(name = "create_user", length = 100)
    private String createUser;

    @Column(name = "create_at", updatable = false)
    private Instant createAt;

    protected DocumentRevisionEntity() {
    }

    public DocumentRevisionEntity(DocumentEntity document, int revisionNo, Integer restoredFromNo) {
        this.documentId = document.getDocumentId();
        this.workspaceId = document.getWorkspaceId();
        this.revisionNo = revisionNo;
        this.title = document.getTitle();
        this.blocks = document.getBlocks();
        this.plainText = document.getPlainText();
        this.contentHash = document.getContentHash();
        this.restoredFromNo = restoredFromNo;
        this.createUser = document.getUpdateUser();
        this.createAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public int getRevisionNo() {
        return revisionNo;
    }

    public String getTitle() {
        return title;
    }

    public String getBlocks() {
        return blocks;
    }

    public String getPlainText() {
        return plainText;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Integer getRestoredFromNo() {
        return restoredFromNo;
    }

    public String getCreateUser() {
        return createUser;
    }

    public Instant getCreateAt() {
        return createAt;
    }
}
