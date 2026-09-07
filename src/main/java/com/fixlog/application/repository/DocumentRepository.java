package com.fixlog.application.repository;

import com.fixlog.domain.model.DocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {

    /**
     * 소유자를 조건에 걸지 않는다. 접근 가능 여부는 PermissionResolver가 판정한다.
     */
    Optional<DocumentEntity> findByDocumentIdAndUsable(String documentId, Integer usable);

    List<DocumentEntity> findByFolderIdAndWorkspaceIdAndUsableOrderByOrdinalAscCreateTimeAsc(
            String folderId, String workspaceId, Integer usable);

    List<DocumentEntity> findByFolderIdIsNullAndWorkspaceIdAndUsableOrderByOrdinalAscCreateTimeAsc(
            String workspaceId, Integer usable);

    List<DocumentEntity> findByWorkspaceIdAndUsable(String workspaceId, Integer usable);

    List<DocumentEntity> findByDocumentIdInAndUsable(Collection<String> documentIds, Integer usable);

    /** 같은 폴더 안 최대 ordinal. 문서가 없거나 값이 NULL이면 -1. */
    @Query("""
            select coalesce(max(d.ordinal), -1) from DocumentEntity d
            where (:folderId is null and d.folderId is null or d.folderId = :folderId)
              and d.workspaceId = :workspaceId and d.usable = 1
            """)
    int maxOrdinal(@Param("folderId") String folderId, @Param("workspaceId") String workspaceId);

    Page<DocumentEntity> findByWorkspaceIdAndUsable(String workspaceId, Integer usable, Pageable pageable);

    Page<DocumentEntity> findByFolderIdAndWorkspaceIdAndUsable(
            String folderId, String workspaceId, Integer usable, Pageable pageable);

    // 폴더별 문서 수를 GROUP BY로 세지 않는다. 집계에 접근 불가 문서가 섞이면
    // 개수만으로 그 존재가 드러나므로, 판정을 통과한 문서만 메모리에서 센다(FolderService 참고).
}
