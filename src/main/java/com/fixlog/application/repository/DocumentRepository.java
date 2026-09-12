package com.fixlog.application.repository;

import com.fixlog.domain.model.DocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 조회 조건은 워크스페이스까지만 건다. 누가 볼 수 있는지는 여기서 판단하지 않는다.
 *
 * <p>권한 조건을 쿼리에 넣으면 새 쿼리를 추가할 때마다 조건을 다시 써야 하고,
 * 한 번만 빠뜨려도 권한 우회가 된다. 판정은 {@code PermissionEvaluator} 한 곳에서만 한다
 * (FR-PRM-003, NFR-002).
 */
public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {

    Optional<DocumentEntity> findByDocumentIdAndUsable(String documentId, Integer usable);

    List<DocumentEntity> findByWorkspaceIdAndFolderIdAndUsableOrderByOrdinalAscCreateTimeAsc(
            UUID workspaceId, String folderId, Integer usable);

    List<DocumentEntity> findByWorkspaceIdAndFolderIdIsNullAndUsableOrderByOrdinalAscCreateTimeAsc(
            UUID workspaceId, Integer usable);

    /**
     * 워크스페이스의 활성 문서 전량. 목록은 권한으로 걸러낸 뒤 페이지를 잘라야 하는데,
     * 권한 조건을 쿼리에 넣지 않기로 했으므로 걸러내기 전 집합을 여기서 가져온다.
     */
    List<DocumentEntity> findByWorkspaceIdAndUsableOrderByOrdinalAscCreateTimeAsc(
            UUID workspaceId, Integer usable);

    /** 휴지통 목록용. 삭제된 문서를 최근 삭제순으로 가져온다. */
    List<DocumentEntity> findByWorkspaceIdAndUsableOrderByDeletedAtDesc(UUID workspaceId, Integer usable);

    /** 같은 폴더 안 최대 ordinal. 문서가 없거나 값이 NULL이면 -1. */
    @Query("""
            select coalesce(max(d.ordinal), -1) from DocumentEntity d
            where (:folderId is null and d.folderId is null or d.folderId = :folderId)
              and d.workspaceId = :workspaceId and d.usable = 1
            """)
    int maxOrdinal(@Param("folderId") String folderId, @Param("workspaceId") UUID workspaceId);

    Page<DocumentEntity> findByWorkspaceIdAndUsable(UUID workspaceId, Integer usable, Pageable pageable);

    Page<DocumentEntity> findByWorkspaceIdAndFolderIdAndUsable(
            UUID workspaceId, String folderId, Integer usable, Pageable pageable);

    /** 폴더별 직속 문서 수. 루트 문서(folderId is null)는 집계에서 제외된다. */
    @Query("""
            select d.folderId as folderId, count(d) as documentCount from DocumentEntity d
            where d.workspaceId = :workspaceId and d.usable = 1 and d.folderId is not null
            group by d.folderId
            """)
    List<FolderDocumentCount> countDocumentsByFolder(@Param("workspaceId") UUID workspaceId);

    long countByWorkspaceIdAndUsable(UUID workspaceId, Integer usable);

    /** 관리자 콘솔의 사용자별 분포. */
    @Query("""
            select d.createUser as userId, count(d) as documentCount from DocumentEntity d
            where d.workspaceId = :workspaceId and d.usable = 1
            group by d.createUser
            """)
    List<UserDocumentCount> countDocumentsByUser(@Param("workspaceId") UUID workspaceId);

    interface UserDocumentCount {
        String getUserId();

        long getDocumentCount();
    }

    interface FolderDocumentCount {
        String getFolderId();

        long getDocumentCount();
    }

    /** 워크스페이스 삭제 시 내부 문서 일괄 soft-delete. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE DocumentEntity d SET d.usable = 0 WHERE d.workspaceId = :workspaceId AND d.usable = 1")
    void softDeleteByWorkspaceId(@Param("workspaceId") UUID workspaceId);
}
