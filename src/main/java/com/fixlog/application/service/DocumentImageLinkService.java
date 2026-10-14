package com.fixlog.application.service;

import com.fixlog.application.event.DocumentSavedEvent;
import com.fixlog.application.repository.ImageRepository;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.ImageEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 문서가 참조하는 이미지에 문서 id를 채운다.
 *
 * <p>이미지는 문서가 저장되기 전에 올라오므로 업로드 시점엔 어느 문서 것인지 알 수 없다.
 * 이 연결은 <b>분석이 끝난 뒤 어떤 문서를 다시 색인할지</b> 되짚는 데 쓰인다.
 *
 * <p>커밋 전에 같은 트랜잭션 안에서 처리해 문서 본문과 연결이 어긋나지 않게 한다.
 */
@Service
public class DocumentImageLinkService {

    private static final Logger log = LoggerFactory.getLogger(DocumentImageLinkService.class);

    private final ImageRepository imageRepository;
    private final DocumentImageReferenceExtractor referenceExtractor;

    public DocumentImageLinkService(ImageRepository imageRepository,
                                    DocumentImageReferenceExtractor referenceExtractor) {
        this.imageRepository = imageRepository;
        this.referenceExtractor = referenceExtractor;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onDocumentSaved(DocumentSavedEvent event) {
        DocumentEntity doc = event.document();
        try {
            link(doc);
        } catch (Exception e) {
            // 이미지 연결은 검색 보조용이다. 여기서 터져 문서 저장 자체가 깨지면 안 된다.
            log.error("문서-이미지 연결 실패: documentId={}", doc.getDocumentId(), e);
        }
    }

    private void link(DocumentEntity doc) {
        List<String> imageIds = referenceExtractor.extract(doc.getBlocks());
        if (imageIds.isEmpty()) {
            return;
        }

        List<ImageEntity> images = imageRepository.findByImageIdIn(imageIds);
        for (ImageEntity image : images) {
            image.linkToDocumentIfAbsent(doc.getDocumentId());
        }
        imageRepository.saveAll(images);
    }
}
