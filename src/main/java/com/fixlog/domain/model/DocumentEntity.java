package com.fixlog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "apj_document")
public class DocumentEntity {

    @Id
    @Column(name = "document_id", length = 100)
    private String documentId;

    /** 소속 워크스페이스. 문서는 정확히 하나의 워크스페이스에 속한다 (FR-WS-003). */
    @Column(name = "workspace_id", columnDefinition = "uuid", nullable = false)
    private UUID workspaceId;

    @Column(name = "folder_id", length = 100)
    private String folderId;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "blocks", columnDefinition = "TEXT")
    private String blocks;

    @Column(name = "plain_text", columnDefinition = "TEXT")
    private String plainText;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "ordinal")
    private Integer ordinal;

    @Column(name = "usable")
    private Integer usable;

    /** 휴지통 목록에 "언제 누가 지웠는지"를 보여주기 위해 따로 남긴다. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    @Column(name = "create_user", length = 100)
    private String createUser;

    @Column(name = "create_time", updatable = false)
    private Instant createTime;

    @Column(name = "update_user", length = 100)
    private String updateUser;

    @Column(name = "update_time")
    private Instant updateTime;

    protected DocumentEntity() {
    }

    public DocumentEntity(String documentId, UUID workspaceId, String folderId,
                          String title, String blocks, String plainText, String contentHash,
                          Integer ordinal, String createUser) {
        this.documentId = documentId;
        this.workspaceId = workspaceId;
        this.folderId = folderId;
        this.title = title;
        this.blocks = blocks;
        this.plainText = plainText;
        this.contentHash = contentHash;
        this.ordinal = ordinal;
        this.usable = 1;
        this.createUser = createUser;
        this.createTime = Instant.now();
        this.updateUser = createUser;
        this.updateTime = Instant.now();
    }

    public void updateContent(String title, String blocks, String plainText, String contentHash, String updateUser) {
        this.title = title;
        this.blocks = blocks;
        this.plainText = plainText;
        this.contentHash = contentHash;
        this.updateUser = updateUser;
        this.updateTime = Instant.now();
    }

    public void updateTitle(String title, String updateUser) {
        this.title = title;
        this.updateUser = updateUser;
        this.updateTime = Instant.now();
    }

    // 순서 재배치는 내용 변경이 아니므로 updateTime을 갱신하지 않는다.
    public void applyOrdinal(int ordinal) {
        this.ordinal = ordinal;
    }

    public void moveTo(String folderId, int ordinal, String updateUser) {
        this.folderId = folderId;
        this.ordinal = ordinal;
        this.updateUser = updateUser;
        this.updateTime = Instant.now();
    }

    /** 행을 지우지 않고 휴지통으로 보낸다. 복원할 수 있어야 하기 때문이다. */
    public void softDelete(String updateUser) {
        this.usable = 0;
        this.deletedAt = Instant.now();
        this.deletedBy = updateUser;
        this.updateUser = updateUser;
        this.updateTime = Instant.now();
    }

    public void restore(String updateUser) {
        this.usable = 1;
        this.deletedAt = null;
        this.deletedBy = null;
        this.updateUser = updateUser;
        this.updateTime = Instant.now();
    }

    public boolean isTrashed() {
        return Integer.valueOf(0).equals(usable);
    }

    public String getDocumentId() { return documentId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getFolderId() { return folderId; }
    public String getTitle() { return title; }
    public String getBlocks() { return blocks; }
    public String getPlainText() { return plainText; }
    public String getContentHash() { return contentHash; }
    public Integer getOrdinal() { return ordinal; }
    public Integer getUsable() { return usable; }
    public String getCreateUser() { return createUser; }
    public Instant getCreateTime() { return createTime; }
    public String getUpdateUser() { return updateUser; }
    public Instant getUpdateTime() { return updateTime; }
    public Instant getDeletedAt() { return deletedAt; }
    public String getDeletedBy() { return deletedBy; }
}
