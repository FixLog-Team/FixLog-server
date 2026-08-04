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

    List<PermissionEntity> findByResourceTypeAndResourceId(ResourceType resourceType, String resourceId);

    Optional<PermissionEntity> findByResourceTypeAndResourceIdAndPrincipalTypeAndPrincipalId(
            ResourceType resourceType, String resourceId, PrincipalType principalType, UUID principalId);
}
