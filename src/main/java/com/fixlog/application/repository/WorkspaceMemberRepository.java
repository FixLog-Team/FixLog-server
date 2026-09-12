package com.fixlog.application.repository;

import com.fixlog.domain.model.WorkspaceMemberEntity;
import com.fixlog.domain.model.WorkspaceRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMemberEntity, UUID> {

    Optional<WorkspaceMemberEntity> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    List<WorkspaceMemberEntity> findByUserId(UUID userId);

    List<WorkspaceMemberEntity> findByWorkspaceIdOrderByCreateAtAsc(UUID workspaceId);

    boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    long countByWorkspaceIdAndRole(UUID workspaceId, WorkspaceRole role);

    void deleteByWorkspaceId(UUID workspaceId);
}
