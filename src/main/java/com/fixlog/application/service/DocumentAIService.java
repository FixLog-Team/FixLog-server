package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentAiSummaryRepository;
import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.config.AiUsageProperties;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.AiModelTier;
import com.fixlog.domain.model.DocumentAiSummaryEntity;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.PermissionAction;
import com.fixlog.domain.model.ResourceType;
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
    private final PermissionEvaluator permissionEvaluator;
    private final AiUsageService aiUsageService;
    private final WorkspaceContext workspaceContext;
    private final AiUsageProperties aiUsageProperties;

    public DocumentAIService(@Qualifier("googleGenAiChatModel") ChatModel chatModel,
                             DocumentRepository documentRepository,
                             DocumentAiSummaryRepository summaryRepository,
                             PermissionEvaluator permissionEvaluator,
                             AiUsageService aiUsageService,
                             WorkspaceContext workspaceContext,
                             AiUsageProperties aiUsageProperties) {
        super(ChatClient.builder(chatModel).build());
        this.documentRepository = documentRepository;
        this.summaryRepository = summaryRepository;
        this.permissionEvaluator = permissionEvaluator;
        this.aiUsageService = aiUsageService;
        this.workspaceContext = workspaceContext;
        this.aiUsageProperties = aiUsageProperties;
    }

    /**
     * 문서 요약. 권한 판정(FR-AI-011) → 한도 확인(FR-AI-006) → 호출 → 사용량 기록(FR-AI-005) 순이다.
     *
     * <p>실패해도 기록은 남긴다. 실패한 호출까지 봐야 어디서 무엇이 터지는지 알 수 있다.
     */
    public String summarizeDocumentById(String documentId) {
        permissionEvaluator.require(ResourceType.DOCUMENT, documentId, PermissionAction.VIEW);
        DocumentEntity doc = documentRepository
                .findByDocumentIdAndUsable(documentId, Integer.valueOf(1))
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "문서를 찾을 수 없습니다."));

        DocumentAiSummaryEntity cached = summaryRepository.findById(documentId).orElse(null);
        if (cached != null && cached.matches(doc.getContentHash())) {
            log.info("요약 캐시 적중: documentId={}", documentId);
            return cached.getSummary();
        }

        String summary = withUsageTracking(doc.getWorkspaceId(),
                () -> summarizeDocumentWithUsage(doc.getPlainText()));
        saveSummaryCache(cached, doc, summary);
        return summary;
    }

    /** 문서에 매이지 않은 요약. 현재 워크스페이스의 한도를 쓴다. */
    public String summarizeFreeText(String content) {
        return withUsageTracking(workspaceContext.requireCurrentWorkspaceId(),
                () -> summarizeDocumentWithUsage(content));
    }

    private String withUsageTracking(java.util.UUID workspaceId,
                                     java.util.function.Supplier<AiResult> call) {
        java.util.UUID userId = workspaceContext.requireCurrentUserId();
        String model = aiUsageProperties.getFreeModel();

        aiUsageService.requireFreeQuota(workspaceId);
        try {
            AiResult result = call.get();
            aiUsageService.record(workspaceId, userId, AiModelTier.FREE, model,
                    result.inputTokens(), result.outputTokens(), true);
            return result.content();
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            aiUsageService.record(workspaceId, userId, AiModelTier.FREE, model, 0L, 0L, false);
            throw e;
        }
    }

    private void saveSummaryCache(DocumentAiSummaryEntity cached, DocumentEntity doc, String summary) {
        try {
            if (cached != null) {
                cached.updateSummary(doc.getContentHash(), summary);
                summaryRepository.save(cached);
            } else {
                summaryRepository.save(new DocumentAiSummaryEntity(
                        doc.getDocumentId(), doc.getContentHash(), summary));
            }
        } catch (Exception e) {
            log.warn("요약 캐시 저장 실패: documentId={}", doc.getDocumentId(), e);
        }
    }
}
