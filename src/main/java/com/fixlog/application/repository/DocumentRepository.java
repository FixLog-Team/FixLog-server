package com.fixlog.application.repository;

import com.fixlog.domain.model.DocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {
    Optional<DocumentEntity> findByDocumentIdAndCreateUserAndUsable(
            String documentId, String createUser, Integer usable);

    List<DocumentEntity> findByFolderIdAndCreateUserAndUsableOrderByOrdinalAscCreateTimeAsc(
            String folderId, String createUser, Integer usable);

    List<DocumentEntity> findByFolderIdIsNullAndCreateUserAndUsableOrderByOrdinalAscCreateTimeAsc(
            String createUser, Integer usable);

    /** 같은 폴더 안 최대 ordinal. 문서가 없거나 값이 NULL이면 -1. */
    @Query("""
            select coalesce(max(d.ordinal), -1) from DocumentEntity d
            where (:folderId is null and d.folderId is null or d.folderId = :folderId)
              and d.createUser = :createUser and d.usable = 1
            """)
    int maxOrdinal(@Param("folderId") String folderId, @Param("createUser") String createUser);

    Page<DocumentEntity> findByCreateUserAndUsable(String createUser, Integer usable, Pageable pageable);

    Page<DocumentEntity> findByFolderIdAndCreateUserAndUsable(
            String folderId, String createUser, Integer usable, Pageable pageable);

    /** 폴더별 직속 문서 수. 루트 문서(folderId is null)는 집계에서 제외된다. */
    @Query("""
            select d.folderId as folderId, count(d) as documentCount from DocumentEntity d
            where d.createUser = :createUser and d.usable = 1 and d.folderId is not null
            group by d.folderId
            """)
    List<FolderDocumentCount> countDocumentsByFolder(@Param("createUser") String createUser);

    interface FolderDocumentCount {
        String getFolderId();

        long getDocumentCount();
    }
}
