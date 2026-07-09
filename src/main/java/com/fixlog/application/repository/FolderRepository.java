package com.fixlog.application.repository;

import com.fixlog.domain.model.FolderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<FolderEntity, String> {
    List<FolderEntity> findByCreateUserAndUsable(String createUser, Integer usable);

    Optional<FolderEntity> findByFolderIdAndCreateUser(String folderId, String createUser);

    List<FolderEntity> findByParentIdAndCreateUserAndUsableOrderByOrdinalAscCreateTimeAsc(
            String parentId, String createUser, Integer usable);

    List<FolderEntity> findByParentIdIsNullAndCreateUserAndUsableOrderByOrdinalAscCreateTimeAsc(
            String createUser, Integer usable);

    /** 형제 중 최대 ordinal. 형제가 없거나 값이 NULL이면 -1. */
    @Query("""
            select coalesce(max(f.ordinal), -1) from FolderEntity f
            where (:parentId is null and f.parentId is null or f.parentId = :parentId)
              and f.createUser = :createUser and f.usable = 1
            """)
    int maxOrdinal(@Param("parentId") String parentId, @Param("createUser") String createUser);
}
