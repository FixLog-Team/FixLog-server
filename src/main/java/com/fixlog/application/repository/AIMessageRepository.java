package com.fixlog.application.repository;

import com.fixlog.domain.model.AIMessageEntity;
import com.fixlog.domain.model.AIMessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AIMessageRepository extends JpaRepository<AIMessageEntity, UUID> {

    Page<AIMessageEntity> findByConversationIdOrderByMessageSequenceDesc(
            UUID conversationId, Pageable pageable);

    Optional<AIMessageEntity> findByMessageIdAndConversationId(UUID messageId, UUID conversationId);

    Slice<AIMessageEntity> findByConversationIdAndMessageSequenceLessThanOrderByMessageSequenceDesc(
            UUID conversationId, Integer beforeSequence, Pageable pageable);

    /** 현재 순번 이전의 완료 메시지를 최근순으로 조회 (대화 맥락 윈도우용). */
    List<AIMessageEntity> findByConversationIdAndStatusAndMessageSequenceLessThanOrderByMessageSequenceDesc(
            UUID conversationId, AIMessageStatus status, Integer messageSequence, Pageable pageable);
}
