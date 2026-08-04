package com.fixlog.application.repository;

import com.fixlog.domain.model.GroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<GroupEntity, UUID> {

    List<GroupEntity> findByWorkspaceIdOrderByCreateAtAsc(UUID workspaceId);

    /** 그룹 조회는 항상 워크스페이스를 함께 건다. 그룹은 워크스페이스 경계를 넘지 않는다. */
    Optional<GroupEntity> findByGroupIdAndWorkspaceId(UUID groupId, UUID workspaceId);

    boolean existsByWorkspaceIdAndGroupName(UUID workspaceId, String groupName);
}
