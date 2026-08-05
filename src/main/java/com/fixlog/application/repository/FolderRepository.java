package com.fixlog.application.repository;

import com.fixlog.domain.model.FolderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 조회 조건은 워크스페이스까지만 건다. 접근 가능 여부 판정은 {@code PermissionEvaluator}가 한다
 * (FR-PRM-003, NFR-002).
 */
public interface FolderRepository extends JpaRepository<FolderEntity, String> {

    List<FolderEntity> findByWorkspaceIdAndUsable(UUID workspaceId, Integer usable);

    /** 휴지통 목록용. */
    List<FolderEntity> findByWorkspaceIdAndUsableOrderByDeletedAtDesc(UUID workspaceId, Integer usable);

    Optional<FolderEntity> findByFolderIdAndUsable(String folderId, Integer usable);

    List<FolderEntity> findByWorkspaceIdAndParentIdAndUsableOrderByOrdinalAscCreateTimeAsc(
            UUID workspaceId, String parentId, Integer usable);

    List<FolderEntity> findByWorkspaceIdAndParentIdIsNullAndUsableOrderByOrdinalAscCreateTimeAsc(
            UUID workspaceId, Integer usable);

    /** 형제 중 최대 ordinal. 형제가 없거나 값이 NULL이면 -1. */
    @Query("""
            select coalesce(max(f.ordinal), -1) from FolderEntity f
            where (:parentId is null and f.parentId is null or f.parentId = :parentId)
              and f.workspaceId = :workspaceId and f.usable = 1
            """)
    int maxOrdinal(@Param("parentId") String parentId, @Param("workspaceId") UUID workspaceId);

    /**
     * 경로 접두사로 서브트리를 통째로 가져온다. 경로 조각이 폴더 UUID라 접두사는 전역에서 유일하므로
     * 워크스페이스 조건 없이도 다른 워크스페이스의 폴더가 섞이지 않는다.
     */
    List<FolderEntity> findByPathStartingWith(String pathPrefix);
}
