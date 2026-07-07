package com.fixlog.application.repository;

import com.fixlog.domain.model.FolderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<FolderEntity, String> {
    List<FolderEntity> findByCreateUserAndUsable(String createUser, Integer usable);

    Optional<FolderEntity> findByFolderIdAndCreateUser(String folderId, String createUser);

    List<FolderEntity> findByParentIdAndCreateUserAndUsable(String parentId, String createUser, Integer usable);

    List<FolderEntity> findByParentIdIsNullAndCreateUserAndUsable(String createUser, Integer usable);
}
