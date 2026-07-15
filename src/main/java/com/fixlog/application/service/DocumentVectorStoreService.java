package com.fixlog.application.service;

import com.fixlog.application.event.DocumentDeletedEvent;
import com.fixlog.application.event.DocumentSavedEvent;
import com.fixlog.domain.model.DocumentEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentVectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(DocumentVectorStoreService.class);

    private final VectorStore vectorStore;
    private final DocumentChunkingService chunkingService;
    private final JdbcTemplate pgVectorJdbcTemplate;

    public DocumentVectorStoreService(VectorStore vectorStore,
                                      DocumentChunkingService chunkingService,
                                      @Qualifier("pgVectorJdbcTemplate") JdbcTemplate pgVectorJdbcTemplate) {
        this.vectorStore = vectorStore;
        this.chunkingService = chunkingService;
        this.pgVectorJdbcTemplate = pgVectorJdbcTemplate;
    }

    /**
     * 문서 저장 트랜잭션 커밋 이후 비동기로 벡터 인덱싱을 수행한다.
     * 기존 청크를 삭제하고 새 청크를 임베딩하여 pgvector에 저장한다.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentSaved(DocumentSavedEvent event) {
        DocumentEntity doc = event.document();
        try {
            deleteChunks(doc.getDocumentId());
            int chunkCount = indexDocument(doc);
            log.info("벡터 인덱싱 완료: documentId={}, 청크 수={}", doc.getDocumentId(), chunkCount);
        } catch (Exception e) {
            log.error("벡터 인덱싱 실패: documentId={}", doc.getDocumentId(), e);
        }
    }

    /**
     * 문서 삭제(소프트 딜리트) 트랜잭션 커밋 이후 비동기로 벡터를 제거한다.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentDeleted(DocumentDeletedEvent event) {
        try {
            deleteChunks(event.documentId());
            log.info("벡터 삭제 완료: documentId={}", event.documentId());
        } catch (Exception e) {
            log.error("벡터 삭제 실패: documentId={}", event.documentId(), e);
        }
    }

    private int indexDocument(DocumentEntity doc) {
        List<String> chunks = chunkingService.chunk(doc.getPlainText());
        if (chunks.isEmpty()) return 0;

        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("documentId", doc.getDocumentId());
            metadata.put("title", doc.getTitle());
            metadata.put("folderId", doc.getFolderId() != null ? doc.getFolderId() : "");
            metadata.put("createUser", doc.getCreateUser());
            metadata.put("createTime", doc.getCreateTime().toString());
            metadata.put("chunkIndex", i);
            metadata.put("totalChunks", chunks.size());

            documents.add(new Document(chunks.get(i), metadata));
        }

        vectorStore.add(documents);
        return chunks.size();
    }

    private void deleteChunks(String documentId) {
        pgVectorJdbcTemplate.update(
                "DELETE FROM document_embeddings WHERE metadata->>'documentId' = ?",
                documentId
        );
    }
}
