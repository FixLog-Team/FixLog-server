package com.fixlog.application.repository;

import com.fixlog.domain.model.PermissionOverrideEntity;
import com.fixlog.domain.model.PermissionOverrideId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionOverrideRepository
        extends JpaRepository<PermissionOverrideEntity, PermissionOverrideId> {

    Optional<PermissionOverrideEntity> findByNodeIdAndUserId(String nodeId, UUID userId);

    /** 특정 노드의 사용자별 설정 전체. 관리 화면의 노드 상세용. */
    List<PermissionOverrideEntity> findByNodeId(String nodeId);

    /**
     * 워크스페이스 안에서 한 사용자에게 걸린 설정 전체.
     * 부모 체인을 매 레벨마다 조회하지 않기 위해 한 번에 읽어 메모리에서 참조한다.
     */
    List<PermissionOverrideEntity> findByWorkspaceIdAndUserId(String workspaceId, UUID userId);

    void deleteByNodeIdAndUserId(String nodeId, UUID userId);
}
