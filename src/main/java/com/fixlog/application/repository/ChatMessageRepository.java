package com.fixlog.application.repository;

import com.fixlog.domain.model.ChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, String> {

    /** 최근 메시지부터 조회 (슬라이딩 윈도우용). 사용 시 시간순으로 뒤집어 사용한다. */
    List<ChatMessageEntity> findByConversationIdAndCreateUserOrderByCreateTimeDesc(
            String conversationId, String createUser, Pageable pageable);

    /** 대화 전체 이력을 시간순으로 조회 (이력 조회 API용). */
    List<ChatMessageEntity> findByConversationIdAndCreateUserOrderByCreateTimeAsc(
            String conversationId, String createUser);

    boolean existsByConversationIdAndCreateUser(String conversationId, String createUser);
}
