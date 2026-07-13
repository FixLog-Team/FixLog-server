package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.security.SecurityUtil;
import com.fixlog.domain.model.DocumentEntity;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class DocumentAIService extends AbstractAIService {

    private final DocumentRepository documentRepository;

    public DocumentAIService(@Qualifier("googleGenAiChatModel") ChatModel chatModel,
                             DocumentRepository documentRepository) {
        super(ChatClient.builder(chatModel).build());
        this.documentRepository = documentRepository;
    }

    public String summarizeDocument(String documentId) {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(Code.UNAUTHORIZED, "인증 정보가 없습니다.");
        }
        DocumentEntity doc = documentRepository
                .findByDocumentIdAndCreateUserAndUsable(documentId, userId, Integer.valueOf(1))
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "문서를 찾을 수 없습니다."));
        return summarizeDocument(doc.getPlainText());
    }
}
