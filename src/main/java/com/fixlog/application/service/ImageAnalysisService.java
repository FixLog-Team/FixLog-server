package com.fixlog.application.service;

import com.fixlog.application.event.ImageAnalyzedEvent;
import com.fixlog.application.event.ImageUploadedEvent;
import com.fixlog.application.repository.ImageRepository;
import com.fixlog.domain.model.ImageEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 이미지 비전 분석의 진행을 맡는다.
 *
 * <p>업로드 응답을 막지 않도록 분석은 비동기로 돌린다. 분석 자체는 트랜잭션 밖에서 수행하고
 * 상태 기록만 짧은 트랜잭션으로 끊는다. 비전 모델 호출이 수 초 걸리는 동안 커넥션을 쥐고 있으면
 * 문서 저장·검색이 쓸 커넥션이 마르기 때문이다.
 */
@Service
public class ImageAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ImageAnalysisService.class);

    private final ImageRepository imageRepository;
    private final ImageStorageService imageStorageService;
    private final DocumentImageAnalyzer analyzer;
    private final ImageAnalysisQuotaService quotaService;
    private final ApplicationEventPublisher eventPublisher;

    public ImageAnalysisService(ImageRepository imageRepository,
                                ImageStorageService imageStorageService,
                                DocumentImageAnalyzer analyzer,
                                ImageAnalysisQuotaService quotaService,
                                ApplicationEventPublisher eventPublisher) {
        this.imageRepository = imageRepository;
        this.imageStorageService = imageStorageService;
        this.analyzer = analyzer;
        this.quotaService = quotaService;
        this.eventPublisher = eventPublisher;
    }

    @Async("imageAnalysisExecutor")
    @EventListener
    public void onImageUploaded(ImageUploadedEvent event) {
        analyze(event.imageId());
    }

    public void analyze(String imageId) {
        ImageEntity image = imageRepository.findById(imageId).orElse(null);
        if (image == null || !image.needsAnalysis()) {
            return;
        }

        if (!analyzer.canAnalyze(image.getContentType())) {
            markSkipped(imageId, "비전 모델이 읽을 수 없는 형식: " + image.getContentType());
            return;
        }

        // 상한에 걸리면 PENDING 으로 남겨둔다. 이미지 사용은 막지 않고, 다음 날 정비 작업이 다시 집어간다.
        if (!quotaService.tryConsume(image.getWorkspaceId())) {
            log.warn("일일 이미지 분석 상한 도달로 분석 보류: workspaceId={}, imageId={}, 상한={}",
                    image.getWorkspaceId(), imageId, quotaService.getDailyLimit());
            return;
        }

        String analysisText;
        try {
            analysisText = analyzer.analyze(imageStorageService.download(image), image.getContentType());
        } catch (Exception e) {
            log.error("이미지 분석 실패: imageId={}", imageId, e);
            markFailed(imageId, e.getMessage());
            return;
        }

        // 분석 중에 문서 저장이 끝나 연결이 생겼을 수 있으므로 최신 상태를 다시 읽어 판단한다
        String documentId = markCompleted(imageId, analysisText);
        log.info("이미지 분석 완료: imageId={}, documentId={}", imageId, documentId);

        if (documentId != null) {
            eventPublisher.publishEvent(new ImageAnalyzedEvent(imageId, documentId));
        }
    }

    /**
     * 아래 기록 메서드들에 {@code @Transactional}을 붙이지 않는 이유:
     * 같은 빈 안에서 부르면 프록시를 거치지 않아 어차피 적용되지 않는다.
     * 한 건을 읽어 고치고 저장하는 일이라 {@code save()}가 여는 트랜잭션만으로 충분하다.
     */
    private String markCompleted(String imageId, String analysisText) {
        ImageEntity image = imageRepository.findById(imageId).orElseThrow();
        image.completeAnalysis(analysisText);
        imageRepository.save(image);
        return image.getDocumentId();
    }

    private void markFailed(String imageId, String error) {
        imageRepository.findById(imageId).ifPresent(image -> {
            image.failAnalysis(error);
            imageRepository.save(image);
        });
    }

    private void markSkipped(String imageId, String reason) {
        imageRepository.findById(imageId).ifPresent(image -> {
            image.skipAnalysis(reason);
            imageRepository.save(image);
        });
    }
}
