package com.fixlog.domain.model;

import java.io.Serializable;
import java.util.Objects;

public class FolderId implements Serializable {
    private String folderId;
    private String workspaceId;

    public FolderId() {
    }

    public FolderId(String folderId, String workspaceId) {
        this.folderId = folderId;
        this.workspaceId = workspaceId;
    }

    public String getFolderId() {
        return folderId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FolderId that = (FolderId) o;
        return Objects.equals(folderId, that.folderId) &&
                Objects.equals(workspaceId, that.workspaceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(folderId, workspaceId);
    }
}
