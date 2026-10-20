package com.fixlog.application.service;

import com.fixlog.application.event.DocumentDeletedEvent;
import com.fixlog.application.event.DocumentSavedEvent;
import com.fixlog.application.event.ImageAnalyzedEvent;
import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.ImageRepository;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.ImageEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
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

    /** 청크가 이미지에서 나왔는지 구분하는 metadata 키. 검색 결과에서 근거를 짚어주는 데 쓴다. */
    static final String SOURCE_IMAGE = "image";
    static final String SOURCE_TEXT = "text";

    private final VectorStore vectorStore;
    private final DocumentChunkingService chunkingService;
    private final JdbcTemplate pgVectorJdbcTemplate;
    private final ImageRepository imageRepository;
    private final DocumentRepository documentRepository;
    private final DocumentImageReferenceExtractor referenceExtractor;

    public DocumentVectorStoreService(VectorStore vectorStore,
                                      DocumentChunkingService chunkingService,
                                      @Qualifier("pgVectorJdbcTemplate") JdbcTemplate pgVectorJdbcTemplate,
                                      ImageRepository imageRepository,
                                      DocumentRepository documentRepository,
                                      DocumentImageReferenceExtractor referenceExtractor) {
        this.vectorStore = vectorStore;
        this.chunkingService = chunkingService;
        this.pgVectorJdbcTemplate = pgVectorJdbcTemplate;
        this.imageRepository = imageRepository;
        this.documentRepository = documentRepository;
        this.referenceExtractor = referenceExtractor;
    }

    /**
     * 문서 저장 트랜잭션 커밋 이후 비동기로 벡터 인덱싱을 수행한다.
     * 내용이 바뀐 경우에만 기존 청크를 삭제하고 재임베딩한다.
     * 제목만 바뀐 경우 임베딩 없이 청크 metadata의 title만 갱신한다.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentSaved(DocumentSavedEvent event) {
        DocumentEntity doc = event.document();
        try {
            if (event.contentChanged()) {
                deleteChunks(doc.getDocumentId());
                int chunkCount = indexDocument(doc);
                log.info("벡터 인덱싱 완료: documentId={}, 청크 수={}", doc.getDocumentId(), chunkCount);
            } else if (event.titleChanged()) {
                updateChunkTitle(doc.getDocumentId(), doc.getTitle());
                log.info("벡터 인덱싱 스킵(제목만 변경, metadata 갱신): documentId={}", doc.getDocumentId());
            } else {
                log.info("벡터 인덱싱 스킵(내용 미변경): documentId={}", doc.getDocumentId());
            }
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

        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = baseMetadata(doc);
            metadata.put("chunkIndex", i);
            metadata.put("totalChunks", chunks.size());
            metadata.put("source", SOURCE_TEXT);

            documents.add(new Document(chunks.get(i), metadata));
        }

        documents.addAll(imageDocuments(doc));
        if (documents.isEmpty()) return 0;

        vectorStore.add(documents);
        return documents.size();
    }

    /**
     * 문서가 참조하는 이미지 중 분석이 끝난 것을 청크로 만든다.
     *
     * <p>본문 텍스트에 섞지 않고 별도 청크로 두는 이유는 두 가지다. 검색 결과에서 어느 이미지가 걸렸는지
     * 짚어줄 수 있고, 분석이 뒤늦게 끝났을 때 본문 청크를 건드리지 않고 이미지 청크만 다시 넣을 수 있다.
     */
    private List<Document> imageDocuments(DocumentEntity doc) {
        List<String> imageIds = referenceExtractor.extract(doc.getBlocks());
        if (imageIds.isEmpty()) return List.of();

        List<Document> documents = new ArrayList<>();
        for (ImageEntity image : imageRepository.findByImageIdIn(imageIds)) {
            if (!image.hasSearchableText()) continue;

            // 분석 텍스트가 길면 하나의 청크가 통째로 묻히므로 본문과 같은 기준으로 나눈다
            List<String> chunks = chunkingService.chunk(image.getAnalysisText());
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> metadata = baseMetadata(doc);
                metadata.put("source", SOURCE_IMAGE);
                metadata.put("imageId", image.getImageId());
                metadata.put("imageName", image.getOriginalName() != null ? image.getOriginalName() : "");
                metadata.put("chunkIndex", i);
                metadata.put("totalChunks", chunks.size());

                documents.add(new Document(chunks.get(i), metadata));
            }
        }
        return documents;
    }

    private Map<String, Object> baseMetadata(DocumentEntity doc) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("documentId", doc.getDocumentId());
        // 검색 사전 필터의 1차 조건. 없으면 워크스페이스를 넘어 검색된다 (FR-MIG-007)
        metadata.put("workspaceId", doc.getWorkspaceId().toString());
        metadata.put("title", doc.getTitle());
        metadata.put("folderId", doc.getFolderId() != null ? doc.getFolderId() : "");
        metadata.put("createUser", doc.getCreateUser());
        metadata.put("createTime", doc.getCreateTime().toString());
        return metadata;
    }

    /**
     * 이미지 분석이 끝난 뒤 그 문서의 이미지 청크만 다시 넣는다.
     *
     * <p>업로드 직후 바로 문서를 저장하면 색인 시점에 분석이 끝나 있지 않다. 그대로 두면 그 이미지는
     * 문서를 다시 저장할 때까지 검색되지 않으므로 여기서 메운다. 본문 청크는 건드리지 않는다.
     */
    @Async
    @EventListener
    public void onImageAnalyzed(ImageAnalyzedEvent event) {
        try {
            DocumentEntity doc = documentRepository.findById(event.documentId()).orElse(null);
            if (doc == null) {
                return;
            }
            deleteImageChunks(event.documentId());
            List<Document> documents = imageDocuments(doc);
            if (!documents.isEmpty()) {
                vectorStore.add(documents);
            }
            log.info("이미지 청크 재색인 완료: documentId={}, imageId={}, 청크 수={}",
                    event.documentId(), event.imageId(), documents.size());
        } catch (Exception e) {
            log.error("이미지 청크 재색인 실패: documentId={}, imageId={}",
                    event.documentId(), event.imageId(), e);
        }
    }

    private void deleteImageChunks(String documentId) {
        pgVectorJdbcTemplate.update(
                "DELETE FROM document_embeddings " +
                        "WHERE metadata->>'documentId' = ? AND metadata->>'source' = ?",
                documentId, SOURCE_IMAGE
        );
    }

    private void updateChunkTitle(String documentId, String title) {
        pgVectorJdbcTemplate.update(
                "UPDATE document_embeddings SET metadata = jsonb_set(metadata, '{title}', to_jsonb(?::text)) " +
                        "WHERE metadata->>'documentId' = ?",
                title, documentId
        );
    }

    private void deleteChunks(String documentId) {
        pgVectorJdbcTemplate.update(
                "DELETE FROM document_embeddings WHERE metadata->>'documentId' = ?",
                documentId
        );
    }
}
