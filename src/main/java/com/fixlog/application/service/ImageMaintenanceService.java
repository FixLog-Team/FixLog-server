package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.ImageRepository;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.ImageEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 이미지 정비 작업.
 *
 * <p>두 가지를 함께 처리한다. 둘 다 "이 이미지를 아직 어떤 문서가 쓰고 있나"를 묻기 때문이다.
 * <ul>
 *   <li><b>고아 정리</b> — 어느 문서도 참조하지 않는 이미지를 지운다. 안 치우면 스토리지가 단조 증가한다.</li>
 *   <li><b>보류 분석 재시도</b> — 일일 상한에 걸려 분석되지 못한 이미지를 다시 집어간다.
 *       이 경로가 없으면 그 이미지는 문서를 다시 저장할 때까지 영영 검색되지 않는다.</li>
 * </ul>
 */
@Service
public class ImageMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(ImageMaintenanceService.class);

    private final ImageRepository imageRepository;
    private final DocumentRepository documentRepository;
    private final DocumentImageReferenceExtractor referenceExtractor;
    private final ImageStorageService imageStorageService;
    private final ImageAnalysisService imageAnalysisService;
    private final Duration grace;

    public ImageMaintenanceService(ImageRepository imageRepository,
                                   DocumentRepository documentRepository,
                                   DocumentImageReferenceExtractor referenceExtractor,
                                   ImageStorageService imageStorageService,
                                   ImageAnalysisService imageAnalysisService,
                                   @Value("${fixlog.image.cleanup.grace-hours}") long graceHours) {
        this.imageRepository = imageRepository;
        this.documentRepository = documentRepository;
        this.referenceExtractor = referenceExtractor;
        this.imageStorageService = imageStorageService;
        this.imageAnalysisService = imageAnalysisService;
        this.grace = Duration.ofHours(graceHours);
    }

    /**
     * 참조가 끊긴 이미지를 지운다.
     *
     * <p>막 올라온 이미지는 아직 문서에 붙기 전이라 유예 기간을 두고 건너뛴다. 이게 없으면
     * 사용자가 이미지를 넣고 저장하기 전에 정비가 돌아 방금 올린 이미지를 지워버린다.
     */
    @Scheduled(cron = "${fixlog.image.cleanup.cron}")
    public void cleanupOrphans() {
        Instant threshold = Instant.now().minus(grace);
        List<ImageEntity> candidates = imageRepository.findByCreateTimeBefore(threshold);

        int deleted = 0;
        for (ImageEntity image : candidates) {
            try {
                if (isReferenced(image)) continue;
                imageStorageService.delete(image);
                deleted++;
            } catch (Exception e) {
                // 한 건이 실패해도 나머지는 계속 치운다
                log.error("고아 이미지 삭제 실패: imageId={}", image.getImageId(), e);
            }
        }
        log.info("고아 이미지 정리 완료: 검사={}, 삭제={}", candidates.size(), deleted);
    }

    /**
     * 일일 상한에 걸려 분석되지 못한 이미지를 다시 집어간다.
     *
     * <p>문서가 쓰고 있는 것만 대상으로 한다. 아무도 참조하지 않는 이미지는 분석할 이유가 없고,
     * 어차피 고아 정리가 치운다.
     */
    @Scheduled(cron = "${fixlog.image.pending-retry.cron}")
    public void retryPendingAnalysis() {
        List<ImageEntity> pending = imageRepository.findPendingAnalysis();

        int retried = 0;
        for (ImageEntity image : pending) {
            try {
                if (!isReferenced(image)) continue;
                imageAnalysisService.analyze(image.getImageId());
                retried++;
            } catch (Exception e) {
                log.error("보류 이미지 분석 재시도 실패: imageId={}", image.getImageId(), e);
            }
        }
        log.info("보류 이미지 분석 재시도 완료: 대상={}, 시도={}", pending.size(), retried);
    }

    /**
     * 이 이미지를 아직 쓰고 있는 문서가 있는지 본다.
     *
     * <p>휴지통에 있는 문서도 참조로 친다. 복원하면 다시 쓰이는데 그 사이에 원본을 지우면
     * 복원된 문서에 깨진 이미지가 남는다.
     */
    private boolean isReferenced(ImageEntity image) {
        if (image.getDocumentId() == null) {
            return false;
        }

        Optional<DocumentEntity> document = documentRepository.findById(image.getDocumentId());
        if (document.isEmpty()) {
            return false;
        }

        Set<String> referenced = new HashSet<>(referenceExtractor.extract(document.get().getBlocks()));
        return referenced.contains(image.getImageId());
    }
}
