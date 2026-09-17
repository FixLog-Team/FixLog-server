package com.fixlog.application.repository;

import com.fixlog.domain.model.PermissionEntity;
import com.fixlog.domain.model.PrincipalType;
import com.fixlog.domain.model.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<PermissionEntity, UUID> {

    /**
     * 대상 자신과 조상 폴더들에 걸린, 요청자에게 해당하는 권한을 한 번에 가져온다.
     * 폴더 깊이만큼 왕복하지 않기 위해 조상 ID를 미리 펼쳐 넣는다 (NFR-001).
     */
    @Query("""
            select p from PermissionEntity p
            where p.workspaceId = :workspaceId
              and ((p.resourceType = :resourceType and p.resourceId = :resourceId)
                   or (p.resourceType = com.fixlog.domain.model.ResourceType.FOLDER
                       and p.resourceId in :ancestorFolderIds))
              and ((p.principalType = com.fixlog.domain.model.PrincipalType.USER and p.principalId = :userId)
                   or (p.principalType = com.fixlog.domain.model.PrincipalType.GROUP
                       and p.principalId in :groupIds))
            """)
    List<PermissionEntity> findCandidates(@Param("workspaceId") UUID workspaceId,
                                          @Param("resourceType") ResourceType resourceType,
                                          @Param("resourceId") String resourceId,
                                          @Param("ancestorFolderIds") Collection<String> ancestorFolderIds,
                                          @Param("userId") UUID userId,
                                          @Param("groupIds") Collection<UUID> groupIds);

    /**
     * 워크스페이스 안에서 요청자에게 해당하는 권한 전부. 목록 판정처럼 대상이 여러 개일 때
     * 대상마다 조회하지 않기 위한 것이다.
     */
    @Query("""
            select p from PermissionEntity p
            where p.workspaceId = :workspaceId
              and ((p.principalType = com.fixlog.domain.model.PrincipalType.USER and p.principalId = :userId)
                   or (p.principalType = com.fixlog.domain.model.PrincipalType.GROUP
                       and p.principalId in :groupIds))
            """)
    List<PermissionEntity> findForPrincipals(@Param("workspaceId") UUID workspaceId,
                                             @Param("userId") UUID userId,
                                             @Param("groupIds") Collection<UUID> groupIds);

    List<PermissionEntity> findByResourceTypeAndResourceId(ResourceType resourceType, String resourceId);

    /** 관리자 콘솔의 권한 현황. 워크스페이스 밖은 보이지 않는다. */
    List<PermissionEntity> findByWorkspaceIdOrderByCreateAtDesc(UUID workspaceId);

    Optional<PermissionEntity> findByResourceTypeAndResourceIdAndPrincipalTypeAndPrincipalId(
            ResourceType resourceType, String resourceId, PrincipalType principalType, UUID principalId);

    /** 워크스페이스 경계와 무관하게, 특정 사용자에게 직접 ALLOW된 문서 권한 레코드 전체. */
    @Query("""
            select p from PermissionEntity p
            where p.principalType = com.fixlog.domain.model.PrincipalType.USER
              and p.principalId = :userId
              and p.resourceType = com.fixlog.domain.model.ResourceType.DOCUMENT
              and p.permissionType = com.fixlog.domain.model.PermissionType.ALLOW
            """)
    List<PermissionEntity> findDirectDocumentAllowsForUser(@Param("userId") UUID userId);

    /** 워크스페이스 경계와 무관하게, 특정 사용자에게 직접 ALLOW된 폴더 권한 레코드 전체. */
    @Query("""
            select p from PermissionEntity p
            where p.principalType = com.fixlog.domain.model.PrincipalType.USER
              and p.principalId = :userId
              and p.resourceType = com.fixlog.domain.model.ResourceType.FOLDER
              and p.permissionType = com.fixlog.domain.model.PermissionType.ALLOW
            """)
    List<PermissionEntity> findDirectFolderAllowsForUser(@Param("userId") UUID userId);

    void deleteByWorkspaceId(UUID workspaceId);
}
