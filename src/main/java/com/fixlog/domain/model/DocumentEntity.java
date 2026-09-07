package com.fixlog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "apj_document")
public class DocumentEntity {

    @Id
    @Column(name = "document_id", length = 100)
    private String documentId;

    @Column(name = "workspace_id", length = 100)
    private String workspaceId;

    @Column(name = "folder_id", length = 100)
    private String folderId;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    /** 기본 접근 정책. NULL은 INHERIT으로 해석한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "base_access", length = 10)
    private BaseAccess baseAccess;

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

    public DocumentEntity(String documentId, String workspaceId, String folderId,
                          String title, String blocks, String plainText, String contentHash,
                          Integer ordinal, String createUser) {
        this.documentId = documentId;
        this.workspaceId = workspaceId;
        this.folderId = folderId;
        this.title = title;
        this.baseAccess = BaseAccess.INHERIT;
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

    /** 이 문서에서 상속을 끊거나(ALLOW/DENY) 다시 폴더를 따르게(INHERIT) 한다. */
    public void changeBaseAccess(BaseAccess baseAccess, String updateUser) {
        this.baseAccess = baseAccess == null ? BaseAccess.INHERIT : baseAccess;
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

    public void softDelete(String updateUser) {
        this.usable = 0;
        this.updateUser = updateUser;
        this.updateTime = Instant.now();
    }

    public String getDocumentId() { return documentId; }
    public String getWorkspaceId() { return workspaceId; }
    public String getFolderId() { return folderId; }

    /** 저장된 값이 없으면 폴더를 따른다. 기존 행 backfill 없이 동작하기 위한 기본값이다. */
    public BaseAccess getBaseAccess() { return baseAccess == null ? BaseAccess.INHERIT : baseAccess; }
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
}
