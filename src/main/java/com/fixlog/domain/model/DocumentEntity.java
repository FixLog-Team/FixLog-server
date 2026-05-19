package com.fixlog.domain.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "apj_document", schema = "public")
@IdClass(DocumentId.class)
public class DocumentEntity {

    @Id
    @Column(name = "document_id", length = 100)
    private String documentId;

    @Id
    @Column(name = "folder_id", length = 100)
    private String folderId;

    @Id
    @Column(name = "workspace_id", length = 100)
    private String workspaceId;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "old_block_json", columnDefinition = "json")
    private String oldBlockJson;

    @Column(name = "new_block_json", columnDefinition = "json")
    private String newBlockJson;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "ai_summary", length = 500)
    private String aiSummary;

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

    public String getDocumentId() {
        return documentId;
    }

    public String getFolderId() {
        return folderId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public String getTitle() {
        return title;
    }

    public String getOldBlockJson() {
        return oldBlockJson;
    }

    public String getNewBlockJson() {
        return newBlockJson;
    }

    public String getContent() {
        return content;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public Integer getOrdinal() {
        return ordinal;
    }

    public Integer getUsable() {
        return usable;
    }

    public String getCreateUser() {
        return createUser;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public String getUpdateUser() {
        return updateUser;
    }

    public Instant getUpdateTime() {
        return updateTime;
    }
}
