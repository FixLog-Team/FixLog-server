package com.fixlog.domain.model;

import com.fixlog.presentation.dto.request.FolderRequest;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "apj_folder", schema = "public")
@IdClass(FolderId.class)
public class FolderEntity {

    @Id
    @Column(name = "folder_id", length = 100)
    private String folderId;

    @Id
    @Column(name = "workspace_id", length = 100)
    private String workspaceId;

    @Column(name = "parent_id", length = 100)
    private String parentId;

    @Column(name = "folder_name", length = 100, nullable = false)
    private String folderName;

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

    protected FolderEntity() {
    }

    public FolderEntity(String folderId, String workspaceId, String parentId, String folderName, String createUser) {
        this.folderId = folderId;
        this.workspaceId = workspaceId;
        this.parentId = parentId;
        this.folderName = folderName;
        this.ordinal = 0;
        this.usable = 1;
        this.createUser = createUser;
        this.createTime = Instant.now();
        this.updateTime = Instant.now();
    }

    public void updateFolder(FolderRequest request, String updateUser) {
        this.workspaceId = request.getWorkspaceId() == null ? workspaceId : request.getWorkspaceId();
        this.parentId = request.getParentId() == null ? parentId : request.getParentId();
        this.folderName = request.getFolderName() == null ? folderName : request.getFolderName();
        this.ordinal = request.getOrdinal() == null ? ordinal : request.getOrdinal();
        this.updateUser = updateUser;
        this.updateTime = Instant.now();
    }

    public void softDelete(String updateUser) {
        this.usable = 0;
        this.updateUser = updateUser;
        this.updateTime = Instant.now();
    }

    public String getFolderId() {
        return folderId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public String getParentId() {
        return parentId;
    }

    public String getFolderName() {
        return folderName;
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
