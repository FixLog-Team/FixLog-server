package com.fixlog.application.repository;

import com.fixlog.domain.model.WorkspaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, UUID> {

    Optional<WorkspaceEntity> findByPersonalOwnerId(UUID personalOwnerId);
}
