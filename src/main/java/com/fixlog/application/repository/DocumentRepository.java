package com.fixlog.application.repository;

import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.DocumentId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<DocumentEntity, DocumentId> {

    List<DocumentEntity> findByFolderIdAndWorkspaceIdAndUsable(String folderId, String workspaceId, Integer usable);
}
