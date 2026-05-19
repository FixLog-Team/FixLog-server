package com.fixlog.presentation.dto.request;

public class FolderRequest {
    private String workspaceId;
    private String parentId;
    private String folderName;
    private Integer ordinal;

    public FolderRequest() {
    }

    public FolderRequest(String workspaceId, String parentId, String folderName, Integer ordinal) {
        this.workspaceId = workspaceId;
        this.parentId = parentId;
        this.folderName = folderName;
        this.ordinal = ordinal;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public Integer getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(Integer ordinal) {
        this.ordinal = ordinal;
    }
}
