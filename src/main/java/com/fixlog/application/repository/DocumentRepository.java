package com.fixlog.application.repository;

import com.fixlog.domain.model.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {
    Optional<DocumentEntity> findByDocumentIdAndCreateUserAndUsable(
            String documentId, String createUser, Integer usable);

    List<DocumentEntity> findByFolderIdAndWorkspaceIdAndUsable(String folderId, String workspaceId, Integer usable);
}
