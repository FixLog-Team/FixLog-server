package com.fixlog.application.repository;

import com.fixlog.domain.model.AIConversationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AIConversationRepository extends JpaRepository<AIConversationEntity, UUID> {

    Optional<AIConversationEntity> findByConversationIdAndUserIdAndWorkspaceIdAndUsable(
            UUID conversationId, UUID userId, UUID workspaceId, Integer usable);

    Page<AIConversationEntity> findByUserIdAndWorkspaceIdAndUsableOrderByUpdateTimeDesc(
            UUID userId, UUID workspaceId, Integer usable, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from AIConversationEntity c
            where c.conversationId = :conversationId
              and c.userId = :userId
              and c.workspaceId = :workspaceId
              and c.usable = 1
            """)
    Optional<AIConversationEntity> findActiveByIdAndUserIdAndWorkspaceIdForUpdate(
            @Param("conversationId") UUID conversationId,
            @Param("userId") UUID userId,
            @Param("workspaceId") UUID workspaceId);
}
