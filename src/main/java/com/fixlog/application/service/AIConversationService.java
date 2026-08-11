package com.fixlog.application.service;

import com.fixlog.application.repository.AIConversationRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.security.SecurityUtil;
import com.fixlog.domain.model.AIConversationEntity;
import com.fixlog.presentation.dto.request.AIConversationCreateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AIConversationService {

    private static final String DEFAULT_TITLE = "새 대화";

    private final AIConversationRepository conversationRepository;

    public AIConversationService(AIConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    @Transactional
    public AIConversationEntity create(AIConversationCreateRequest request) {
        UUID userId = requireUserId();
        String title = normalizeTitle(request.title());
        return conversationRepository.save(new AIConversationEntity(userId, title));
    }

    @Transactional(readOnly = true)
    public Page<AIConversationEntity> list(Pageable pageable) {
        return conversationRepository.findByUserIdAndUsableOrderByUpdateTimeDesc(
                requireUserId(), Integer.valueOf(1), pageable);
    }

    @Transactional(readOnly = true)
    public AIConversationEntity get(UUID conversationId) {
        return loadOwned(conversationId);
    }

    @Transactional
    public void delete(UUID conversationId) {
        AIConversationEntity conversation = loadOwned(conversationId);
        conversation.softDelete();
    }

    private AIConversationEntity loadOwned(UUID conversationId) {
        return conversationRepository
                .findByConversationIdAndUserIdAndUsable(conversationId, requireUserId(), Integer.valueOf(1))
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "대화방을 찾을 수 없습니다."));
    }

    private UUID requireUserId() {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(Code.UNAUTHORIZED, "인증 정보가 없습니다.");
        }
        return UUID.fromString(userId);
    }

    private String normalizeTitle(String title) {
        return title == null || title.isBlank() ? DEFAULT_TITLE : title.trim();
    }
}
