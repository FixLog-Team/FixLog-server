package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentAiSummaryRepository;
import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.common.ai.TokenUsageLogger;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.security.SecurityUtil;
import com.fixlog.domain.model.DocumentAiSummaryEntity;
import com.fixlog.domain.model.DocumentEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class DocumentAIService extends AbstractAIService {

    private static final Logger log = LoggerFactory.getLogger(DocumentAIService.class);

    private final DocumentRepository documentRepository;
    private final DocumentAiSummaryRepository summaryRepository;

    public DocumentAIService(@Qualifier("googleGenAiChatModel") ChatModel chatModel,
                             TokenUsageLogger tokenUsageLogger,
                             DocumentRepository documentRepository,
                             DocumentAiSummaryRepository summaryRepository) {
        super(ChatClient.builder(chatModel).build(), tokenUsageLogger);
        this.documentRepository = documentRepository;
        this.summaryRepository = summaryRepository;
    }

    /**
     * 문서 요약. contentHash가 동일한 캐시가 있으면 LLM 호출 없이 캐시를 반환한다.
     */
    public String summarizeDocumentById(String documentId) {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(Code.UNAUTHORIZED, "인증 정보가 없습니다.");
        }
        DocumentEntity doc = documentRepository
                .findByDocumentIdAndCreateUserAndUsable(documentId, userId, Integer.valueOf(1))
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "문서를 찾을 수 없습니다."));

        DocumentAiSummaryEntity cached = summaryRepository.findById(documentId).orElse(null);
        if (cached != null && cached.matches(doc.getContentHash())) {
            log.info("요약 캐시 적중: documentId={}", documentId);
            return cached.getSummary();
        }

        String summary = super.summarizeDocument(doc.getPlainText());
        saveSummaryCache(cached, doc, summary);
        return summary;
    }

    private void saveSummaryCache(DocumentAiSummaryEntity cached, DocumentEntity doc, String summary) {
        try {
            if (cached != null) {
                cached.updateSummary(doc.getContentHash(), summary);
                summaryRepository.save(cached);
            } else {
                summaryRepository.save(new DocumentAiSummaryEntity(doc.getDocumentId(), doc.getContentHash(), summary));
            }
        } catch (Exception e) {
            // 캐시 저장 실패는 요약 반환을 막지 않는다
            log.warn("요약 캐시 저장 실패: documentId={}", doc.getDocumentId(), e);
        }
    }
}
