package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.PermissionAction;
import com.fixlog.domain.model.ResourceType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class DocumentAIService extends AbstractAIService {

    private final DocumentRepository documentRepository;
    private final PermissionEvaluator permissionEvaluator;

    public DocumentAIService(@Qualifier("googleGenAiChatModel") ChatModel chatModel,
                             DocumentRepository documentRepository,
                             PermissionEvaluator permissionEvaluator) {
        super(ChatClient.builder(chatModel).build());
        this.documentRepository = documentRepository;
        this.permissionEvaluator = permissionEvaluator;
    }

    /** AI 처리도 대상 문서의 권한 판정을 선행한다 (FR-AI-011). */
    public String summarizeDocumentById(String documentId) {
        permissionEvaluator.require(ResourceType.DOCUMENT, documentId, PermissionAction.VIEW);
        DocumentEntity doc = documentRepository
                .findByDocumentIdAndUsable(documentId, Integer.valueOf(1))
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "문서를 찾을 수 없습니다."));
        return super.summarizeDocument(doc.getPlainText());
    }
}
