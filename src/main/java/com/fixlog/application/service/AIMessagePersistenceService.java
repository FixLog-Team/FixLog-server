package com.fixlog.application.service;

import com.fixlog.application.repository.AIConversationRepository;
import com.fixlog.application.repository.AIMessageRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.security.SecurityUtil;
import com.fixlog.domain.model.AIConversationEntity;
import com.fixlog.domain.model.AIMessageEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AIMessagePersistenceService {

    private final AIConversationRepository conversationRepository;
    private final AIMessageRepository messageRepository;

    public AIMessagePersistenceService(AIConversationRepository conversationRepository,
                                       AIMessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public PreparedMessages prepare(UUID conversationId, String content) {
        UUID userId = requireUserId();
        AIConversationEntity conversation = conversationRepository
                .findActiveByIdAndUserIdForUpdate(conversationId, userId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "대화방을 찾을 수 없습니다."));

        int userSequence = conversation.reserveMessageSequences(2);
        AIMessageEntity userMessage = AIMessageEntity.createUserMessage(
                conversationId, userSequence, content.trim());
        AIMessageEntity assistantMessage = AIMessageEntity.createPendingAssistantMessage(
                conversationId, userSequence + 1);
        messageRepository.saveAll(List.of(userMessage, assistantMessage));
        return new PreparedMessages(userMessage, assistantMessage);
    }

    @Transactional
    public AIMessageEntity complete(UUID conversationId, UUID messageId, String content,
                                    java.util.List<com.fixlog.presentation.dto.response.SearchResultDto> references) {
        AIMessageEntity message = loadMessage(conversationId, messageId);
        message.complete(content, references);
        return message;
    }

    @Transactional
    public void fail(UUID conversationId, UUID messageId) {
        loadMessage(conversationId, messageId).fail();
    }

    private AIMessageEntity loadMessage(UUID conversationId, UUID messageId) {
        return messageRepository.findByMessageIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "AI 메시지를 찾을 수 없습니다."));
    }

    private UUID requireUserId() {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(Code.UNAUTHORIZED, "인증 정보가 없습니다.");
        }
        return UUID.fromString(userId);
    }

    public record PreparedMessages(
            AIMessageEntity userMessage,
            AIMessageEntity assistantMessage
    ) {
    }
}
