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

    /**
     * 조상 경로를 비정규화해 둔 값 ({@code /루트ID/중간ID/자기ID/}).
     * 권한 상속 판정이 조상 집합을 단일 쿼리로 얻기 위한 것이다 (FR-PRM-007).
     */
    @Column(name = "path", length = 1000, nullable = false)
    private String path;

    @Column(name = "ordinal")
    private Integer ordinal;

    @Column(name = "usable")
    private Integer usable;

    /** 휴지통 목록에 "언제 누가 지웠는지"를 보여주기 위해 따로 남긴다. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    /** false이면 부모 폴더 권한 상속이 끊겨 이 폴더가 독립 권한 섬이 된다. */
    @Column(name = "inherit_from_parent", nullable = false)
    private boolean inheritFromParent = true;

    /** 상속 체인이 끝났을 때 적용하는 기본 접근 값. */
    @Enumerated(EnumType.STRING)
    @Column(name = "base_access", length = 10, nullable = false)
    private PermissionType baseAccess = PermissionType.ALLOW;

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
                        Integer ordinal, String createUser, String parentPath) {
        this.folderId = folderId;
        this.workspaceId = workspaceId;
        this.path = pathUnder(parentPath, folderId);
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

    /** 루트 경로는 {@code /}다. 부모 경로 뒤에 자기 ID를 붙인다. */
    public static String pathUnder(String parentPath, String folderId) {
        String base = (parentPath == null || parentPath.isBlank()) ? "/" : parentPath;
        return base + folderId + "/";
    }

    /**
     * 이동으로 조상이 바뀌면 경로도 함께 바뀐다. 서브트리 전체를 한 번에 갱신해야 하므로
     * 호출자는 {@code FolderService.moveFolder}의 일괄 갱신 경로를 통해서만 쓴다.
     */
    public void applyPath(String path) {
        this.path = path;
    }

    /** 자기 자신을 포함한 조상 폴더 ID들. 권한 상속 판정의 입력이다. */
    public java.util.List<String> pathSegments() {
        return java.util.Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .toList();
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

    public void configureInheritance(boolean inheritFromParent, PermissionType baseAccess) {
        this.inheritFromParent = inheritFromParent;
        this.baseAccess = baseAccess;
    }

    public boolean isInheritFromParent() {
        return inheritFromParent;
    }

    public PermissionType getBaseAccess() {
        return baseAccess;
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

    public String getPath() {
        return path;
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

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public String getDeletedBy() {
        return deletedBy;
    }
}
