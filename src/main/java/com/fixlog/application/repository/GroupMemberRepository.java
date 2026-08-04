package com.fixlog.application.repository;

import com.fixlog.domain.model.GroupMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMemberEntity, UUID> {

    List<GroupMemberEntity> findByGroupIdOrderByCreateAtAsc(UUID groupId);

    List<GroupMemberEntity> findByUserId(UUID userId);

    Optional<GroupMemberEntity> findByGroupIdAndUserId(UUID groupId, UUID userId);

    void deleteByGroupId(UUID groupId);
}
