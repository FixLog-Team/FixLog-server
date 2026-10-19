package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentAiSummaryRepository;
import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.ImageRepository;
import com.fixlog.common.ai.TokenUsageLogger;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.DocumentAiSummaryEntity;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.ImageEntity;
import com.fixlog.domain.model.PermissionAction;
import com.fixlog.domain.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentAIService extends AbstractAIService {

    private static final Logger log = LoggerFactory.getLogger(DocumentAIService.class);

    private final DocumentRepository documentRepository;
    private final DocumentAiSummaryRepository summaryRepository;
    private final ImageRepository imageRepository;
    private final DocumentImageReferenceExtractor referenceExtractor;
    private final PermissionEvaluator permissionEvaluator;

    public DocumentAIService(@Qualifier("googleGenAiChatModel") ChatModel chatModel,
                             TokenUsageLogger tokenUsageLogger,
                             DocumentRepository documentRepository,
                             DocumentAiSummaryRepository summaryRepository,
                             ImageRepository imageRepository,
                             DocumentImageReferenceExtractor referenceExtractor,
                             PermissionEvaluator permissionEvaluator) {
        super(ChatClient.builder(chatModel).build(), tokenUsageLogger);
        this.documentRepository = documentRepository;
        this.summaryRepository = summaryRepository;
        this.imageRepository = imageRepository;
        this.referenceExtractor = referenceExtractor;
        this.permissionEvaluator = permissionEvaluator;
    }

    /** AI 처리도 대상 문서의 권한 판정을 선행한다 (FR-AI-011). */
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

        String contentForSummary = buildContentWithImages(doc);
        String summary = super.summarizeDocument(contentForSummary);
        saveSummaryCache(cached, doc, summary);
        return summary;
    }

    /**
     * 문서 본문 + 첨부 이미지 분석 텍스트를 합쳐 요약 입력을 만든다.
     * 이미지에서 읽어낸 내용도 요약에 반영되어야 문서 전체를 아우르는 요약이 나온다.
     */
    private String buildContentWithImages(DocumentEntity doc) {
        List<String> imageIds = referenceExtractor.extract(doc.getBlocks());
        if (imageIds.isEmpty()) {
            return doc.getPlainText();
        }

        List<ImageEntity> images = imageRepository.findByImageIdIn(imageIds);
        StringBuilder sb = new StringBuilder(doc.getPlainText());
        for (ImageEntity image : images) {
            if (!image.hasSearchableText()) continue;
            sb.append("\n\n[첨부 이미지");
            if (image.getOriginalName() != null) {
                sb.append(": ").append(image.getOriginalName());
            }
            sb.append("]\n").append(image.getAnalysisText());
        }
        return sb.toString();
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
