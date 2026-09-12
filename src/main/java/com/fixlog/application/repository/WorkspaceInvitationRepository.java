package com.fixlog.application.repository;

import com.fixlog.domain.model.InvitationStatus;
import com.fixlog.domain.model.WorkspaceInvitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitationEntity, UUID> {

    Optional<WorkspaceInvitationEntity> findByToken(String token);

    List<WorkspaceInvitationEntity> findByWorkspaceIdOrderByCreateAtDesc(UUID workspaceId);

    boolean existsByWorkspaceIdAndEmailAndStatus(UUID workspaceId, String email, InvitationStatus status);

    void deleteByWorkspaceId(UUID workspaceId);
}
