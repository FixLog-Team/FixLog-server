package com.fixlog.application.repository;

import com.fixlog.domain.model.WorkspaceMemberEntity;
import com.fixlog.domain.model.WorkspaceMemberId;
import com.fixlog.domain.model.WorkspaceRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMemberEntity, WorkspaceMemberId> {

    Optional<WorkspaceMemberEntity> findByWorkspaceIdAndUserId(String workspaceId, UUID userId);

    List<WorkspaceMemberEntity> findByUserId(UUID userId);

    List<WorkspaceMemberEntity> findByWorkspaceIdOrderByCreateTimeAsc(String workspaceId);

    /** 가입 시 자동 생성되는 개인 워크스페이스. 사용자가 OWNER인 가장 오래된 워크스페이스다. */
    Optional<WorkspaceMemberEntity> findFirstByUserIdAndRoleOrderByCreateTimeAsc(UUID userId, WorkspaceRole role);
}
