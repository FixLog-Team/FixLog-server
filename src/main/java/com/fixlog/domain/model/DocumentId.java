package com.fixlog.domain.model;

import java.io.Serializable;
import java.util.Objects;

public class DocumentId implements Serializable {
    private String documentId;
    private String folderId;
    private String workspaceId;

    public DocumentId() {
    }

    public DocumentId(String documentId, String folderId, String workspaceId) {
        this.documentId = documentId;
        this.folderId = folderId;
        this.workspaceId = workspaceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentId that = (DocumentId) o;
        return Objects.equals(documentId, that.documentId) &&
                Objects.equals(folderId, that.folderId) &&
                Objects.equals(workspaceId, that.workspaceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentId, folderId, workspaceId);
    }
}
