package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentHistoryRepository;
import com.fixlog.application.repository.DocumentHistoryRepository.DocumentHistorySummary;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.DocumentHistoryEntity;
import com.fixlog.domain.model.DocumentHistorySource;
import com.fixlog.domain.model.PermissionAction;
import com.fixlog.domain.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DocumentHistoryService {

    private static final Logger log = LoggerFactory.getLogger(DocumentHistoryService.class);

    private final DocumentHistoryRepository historyRepository;
    private final int retentionLimit;
    private final PermissionEvaluator permissionEvaluator;

    public DocumentHistoryService(DocumentHistoryRepository historyRepository,
                                  @Value("${fixlog.document.history.retention:50}") int retentionLimit,
                                  PermissionEvaluator permissionEvaluator) {
        // 0 이하로 설정되면 저장할 때마다 히스토리가 전부 지워지므로 기동 시점에 막는다.
        if (retentionLimit < 1) {
            throw new IllegalArgumentException(
                    "fixlog.document.history.retention 은 1 이상이어야 합니다: " + retentionLimit);
        }
        this.historyRepository = historyRepository;
        this.retentionLimit = retentionLimit;
        this.permissionEvaluator = permissionEvaluator;
    }

    /**
     * 문서의 현재 내용을 히스토리로 밀어넣는다.
     * 반드시 문서를 새 내용으로 갱신하기 <b>전에</b> 호출해야 직전 버전이 보존된다.
     */
    @Transactional
    public void archive(DocumentEntity doc, DocumentHistorySource source) {
        historyRepository.save(new DocumentHistoryEntity(
                UUID.randomUUID().toString(),
                doc.getDocumentId(),
                doc.getTitle(),
                doc.getBlocks(),
                doc.getContentHash(),
                source,
                doc.getUpdateUser()
        ));
        enforceRetention(doc.getDocumentId());
    }

    @Transactional(readOnly = true)
    public Page<DocumentHistorySummary> list(String documentId, Pageable pageable) {
        requireOwnedDocument(documentId);
        return historyRepository.findByDocumentIdOrderByCreateTimeDesc(documentId, pageable);
    }

    @Transactional(readOnly = true)
    public DocumentHistoryEntity getVersion(String documentId, String historyId) {
        requireOwnedDocument(documentId);
        return historyRepository.findByHistoryIdAndDocumentId(historyId, documentId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "문서 히스토리를 찾을 수 없습니다."));
    }

    /** 문서당 보존 개수를 넘긴 오래된 버전을 정리한다. */
    private void enforceRetention(String documentId) {
        if (historyRepository.countByDocumentId(documentId) <= retentionLimit) return;

        List<String> newestFirst = historyRepository.findHistoryIdsNewestFirst(documentId);
        if (newestFirst.size() <= retentionLimit) return;

        List<String> expired = newestFirst.subList(retentionLimit, newestFirst.size());
        historyRepository.deleteAllByIdInBatch(expired);
        // 사용자 데이터가 영구 삭제되는 지점이라 추적 가능하도록 남긴다.
        log.info("히스토리 보존 한도 초과 삭제: documentId={}, 보존={}, 삭제={}",
                documentId, retentionLimit, expired.size());
    }

    private void requireOwnedDocument(String documentId) {
        permissionEvaluator.require(ResourceType.DOCUMENT, documentId, PermissionAction.VIEW);
    }
}
