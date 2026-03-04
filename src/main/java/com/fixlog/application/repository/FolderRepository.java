package com.fixlog.application.repository;

import com.fixlog.domain.model.FolderEntity;
import com.fixlog.domain.model.FolderId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<FolderEntity, FolderId> {
    List<FolderEntity> findByWorkspaceIdAndUsable(String workspaceId, Integer usable);

    Optional<FolderEntity> findByFolderIdAndWorkspaceId(String folderId, String workspaceId);
}
