package com.fixlog.application.service;

import com.fixlog.application.repository.AIMessageRepository;
import com.fixlog.domain.model.AIMessageEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AIMessageQueryService {

    private final AIConversationService conversationService;
    private final AIMessageRepository messageRepository;

    public AIMessageQueryService(AIConversationService conversationService,
                                 AIMessageRepository messageRepository) {
        this.conversationService = conversationService;
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public Slice<AIMessageEntity> list(UUID conversationId, Integer beforeSequence, int size) {
        conversationService.get(conversationId);
        int cursor = beforeSequence == null ? Integer.MAX_VALUE : beforeSequence;
        return messageRepository.findByConversationIdAndMessageSequenceLessThanOrderByMessageSequenceDesc(
                conversationId, cursor, PageRequest.of(0, size));
    }
}
