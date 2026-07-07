package com.fixlog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "apj_document")
public class DocumentEntity {

    @Id
    @Column(name = "document_id", length = 100)
    private String documentId;

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

    public DocumentEntity(String documentId, String folderId,
                          String title, String blocks, String plainText, String contentHash,
                          String createUser) {
        this.documentId = documentId;
        this.folderId = folderId;
        this.title = title;
        this.blocks = blocks;
        this.plainText = plainText;
        this.contentHash = contentHash;
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

    public void moveTo(String folderId, String updateUser) {
        this.folderId = folderId;
        this.updateUser = updateUser;
        this.updateTime = Instant.now();
    }

    public void softDelete(String updateUser) {
        this.usable = 0;
        this.updateUser = updateUser;
        this.updateTime = Instant.now();
    }

    public String getDocumentId() { return documentId; }
    public String getFolderId() { return folderId; }
    public String getTitle() { return title; }
    public String getBlocks() { return blocks; }
    public String getPlainText() { return plainText; }
    public String getContentHash() { return contentHash; }
    public Integer getUsable() { return usable; }
    public String getCreateUser() { return createUser; }
    public Instant getCreateTime() { return createTime; }
    public String getUpdateUser() { return updateUser; }
    public Instant getUpdateTime() { return updateTime; }
}
