package com.fixlog.application.repository;

import com.fixlog.domain.model.FolderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<FolderEntity, String> {

    /**
     * 워크스페이스의 폴더 전량. 권한 판정과 트리 조립 모두 이 결과를 메모리에서 재사용한다.
     * 부모 체인을 매 레벨마다 조회하는 것을 피하기 위한 조회다.
     */
    List<FolderEntity> findByWorkspaceIdAndUsable(String workspaceId, Integer usable);

    /**
     * 소유자를 조건에 걸지 않는다. 접근 가능 여부는 PermissionResolver가 판정한다.
     */
    Optional<FolderEntity> findByFolderIdAndUsable(String folderId, Integer usable);

    Optional<FolderEntity> findByFolderId(String folderId);

    List<FolderEntity> findByParentIdAndWorkspaceIdAndUsableOrderByOrdinalAscCreateTimeAsc(
            String parentId, String workspaceId, Integer usable);

    List<FolderEntity> findByParentIdIsNullAndWorkspaceIdAndUsableOrderByOrdinalAscCreateTimeAsc(
            String workspaceId, Integer usable);

    /** 형제 중 최대 ordinal. 형제가 없거나 값이 NULL이면 -1. */
    @Query("""
            select coalesce(max(f.ordinal), -1) from FolderEntity f
            where (:parentId is null and f.parentId is null or f.parentId = :parentId)
              and f.workspaceId = :workspaceId and f.usable = 1
            """)
    int maxOrdinal(@Param("parentId") String parentId, @Param("workspaceId") String workspaceId);
}
