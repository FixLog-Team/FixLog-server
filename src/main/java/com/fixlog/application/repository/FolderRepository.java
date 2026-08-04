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

    /**
     * 경로 접두사로 서브트리를 통째로 가져온다. 경로 조각이 폴더 UUID라 접두사는 전역에서 유일하므로
     * 소유자 조건 없이도 다른 사용자의 폴더가 섞이지 않는다.
     */
    List<FolderEntity> findByPathStartingWith(String pathPrefix);

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
