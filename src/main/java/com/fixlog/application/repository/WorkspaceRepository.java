package com.fixlog.application.repository;

import com.fixlog.domain.model.WorkspaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, String> {

    Optional<WorkspaceEntity> findByWorkspaceIdAndUsable(String workspaceId, Integer usable);

    List<WorkspaceEntity> findByWorkspaceIdInAndUsable(Collection<String> workspaceIds, Integer usable);
}
