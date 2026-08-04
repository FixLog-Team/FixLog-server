package com.fixlog.domain.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "apj_folder")
public class FolderEntity {

    @Id
    @Column(name = "folder_id", length = 100)
    private String folderId;

    /** 소속 워크스페이스. 폴더는 정확히 하나의 워크스페이스에 속한다 (FR-WS-003). */
    @Column(name = "workspace_id", columnDefinition = "uuid", nullable = false)
    private UUID workspaceId;

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

    public FolderEntity(String folderId, UUID workspaceId, String parentId, String folderName,
                        Integer ordinal, String createUser) {
        this.folderId = folderId;
        this.workspaceId = workspaceId;
        this.parentId = parentId;
        this.folderName = folderName;
        this.ordinal = ordinal;
        this.usable = 1;
        this.createUser = createUser;
        this.createTime = Instant.now();
        this.updateTime = Instant.now();
    }

    public void rename(String folderName, String updateUser) {
        this.folderName = folderName == null ? this.folderName : folderName;
        this.updateUser = updateUser;
        this.updateTime = Instant.now();
    }

    // 순서 재배치는 내용 변경이 아니므로 updateTime을 갱신하지 않는다.
    public void applyOrdinal(int ordinal) {
        this.ordinal = ordinal;
    }

    public void moveTo(String parentId, int ordinal, String updateUser) {
        this.parentId = parentId;
        this.ordinal = ordinal;
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

    public UUID getWorkspaceId() {
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
