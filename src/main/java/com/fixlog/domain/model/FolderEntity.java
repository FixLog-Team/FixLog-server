package com.fixlog.domain.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "apj_folder")
public class FolderEntity {

    @Id
    @Column(name = "folder_id", length = 100)
    private String folderId;

    @Column(name = "workspace_id", length = 100)
    private String workspaceId;

    @Column(name = "parent_id", length = 100)
    private String parentId;

    @Column(name = "folder_name", length = 100, nullable = false)
    private String folderName;

    /** 기본 접근 정책. NULL은 INHERIT으로 해석한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "base_access", length = 10)
    private BaseAccess baseAccess;

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

    public FolderEntity(String folderId, String workspaceId, String parentId, String folderName,
                        Integer ordinal, String createUser) {
        this.folderId = folderId;
        this.workspaceId = workspaceId;
        this.parentId = parentId;
        this.folderName = folderName;
        this.baseAccess = BaseAccess.INHERIT;
        this.ordinal = ordinal;
        this.usable = 1;
        this.createUser = createUser;
        this.createTime = Instant.now();
        this.updateTime = Instant.now();
    }

    /** 이 폴더에서 상속을 끊거나(ALLOW/DENY) 다시 부모를 따르게(INHERIT) 한다. */
    public void changeBaseAccess(BaseAccess baseAccess, String updateUser) {
        this.baseAccess = baseAccess == null ? BaseAccess.INHERIT : baseAccess;
        this.updateUser = updateUser;
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

    public String getWorkspaceId() {
        return workspaceId;
    }

    public String getParentId() {
        return parentId;
    }

    /** 저장된 값이 없으면 부모를 따른다. 기존 행 backfill 없이 동작하기 위한 기본값이다. */
    public BaseAccess getBaseAccess() {
        return baseAccess == null ? BaseAccess.INHERIT : baseAccess;
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
