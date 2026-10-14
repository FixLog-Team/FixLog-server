package com.fixlog;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.ImageAnalysisQuotaRepository;
import com.fixlog.application.repository.ImageRepository;
import com.fixlog.application.service.DocumentImageReferenceExtractor;
import com.fixlog.application.service.ImageAnalysisQuotaService;
import com.fixlog.application.service.ImageAnalysisService;
import com.fixlog.application.service.ImageMaintenanceService;
import com.fixlog.application.service.ImageStorageService;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.ImageAnalysisQuotaEntity;
import com.fixlog.domain.model.ImageEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 이미지 정비: 일일 분석 상한, 고아 정리, 보류 분석 재시도.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImageMaintenanceTest {

    private static final int DAILY_LIMIT = 300;

    @Mock ImageRepository imageRepository;
    @Mock DocumentRepository documentRepository;
    @Mock ImageStorageService imageStorageService;
    @Mock ImageAnalysisService imageAnalysisService;
    @Mock ImageAnalysisQuotaRepository quotaRepository;

    private final DocumentImageReferenceExtractor referenceExtractor = new DocumentImageReferenceExtractor();

    private ImageMaintenanceService maintenanceService;
    private ImageAnalysisQuotaService quotaService;

    private final UUID workspaceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        maintenanceService = new ImageMaintenanceService(
                imageRepository, documentRepository, referenceExtractor,
                imageStorageService, imageAnalysisService, 24);
        quotaService = new ImageAnalysisQuotaService(quotaRepository, DAILY_LIMIT);
        given(quotaRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    // --- 일일 분석 상한 ---

    @Test
    void 상한에_닿기_전까지는_분석을_허용한다() {
        given(quotaRepository.findByWorkspaceIdAndUsageDate(any(), any())).willReturn(Optional.empty());

        assertThat(quotaService.tryConsume(workspaceId)).isTrue();
    }

    @Test
    void 상한에_도달하면_더_이상_허용하지_않는다() {
        ImageAnalysisQuotaEntity quota = usedQuota(DAILY_LIMIT);
        given(quotaRepository.findByWorkspaceIdAndUsageDate(any(), any())).willReturn(Optional.of(quota));

        assertThat(quotaService.tryConsume(workspaceId)).isFalse();
        // 거부된 건은 세지 않는다
        assertThat(quota.getAnalyzedCount()).isEqualTo(DAILY_LIMIT);
    }

    @Test
    void 허용된_건은_사용량으로_센다() {
        ImageAnalysisQuotaEntity quota = usedQuota(10);
        given(quotaRepository.findByWorkspaceIdAndUsageDate(any(), any())).willReturn(Optional.of(quota));

        quotaService.tryConsume(workspaceId);

        assertThat(quota.getAnalyzedCount()).isEqualTo(11);
        verify(quotaRepository).save(quota);
    }

    @Test
    void 워크스페이스마다_따로_센다() {
        UUID other = UUID.randomUUID();
        given(quotaRepository.findByWorkspaceIdAndUsageDate(workspaceId, LocalDate.now()))
                .willReturn(Optional.of(usedQuota(DAILY_LIMIT)));
        given(quotaRepository.findByWorkspaceIdAndUsageDate(other, LocalDate.now()))
                .willReturn(Optional.empty());

        assertThat(quotaService.tryConsume(workspaceId)).isFalse();
        assertThat(quotaService.tryConsume(other)).isTrue();
    }

    // --- 고아 정리 ---

    @Test
    void 아무_문서도_참조하지_않는_이미지는_지운다() {
        ImageEntity orphan = image("img-1", null);
        given(imageRepository.findByCreateTimeBefore(any())).willReturn(List.of(orphan));

        maintenanceService.cleanupOrphans();

        verify(imageStorageService).delete(orphan);
    }

    @Test
    void 문서가_여전히_쓰고_있으면_지우지_않는다() {
        ImageEntity used = image("550e8400-e29b-41d4-a716-446655440000", "doc-1");
        given(imageRepository.findByCreateTimeBefore(any())).willReturn(List.of(used));
        given(documentRepository.findById("doc-1")).willReturn(Optional.of(
                document("[{\"type\":\"image\",\"data\":{\"url\":\"/api/images/550e8400-e29b-41d4-a716-446655440000\"}}]")));

        maintenanceService.cleanupOrphans();

        verify(imageStorageService, never()).delete(any());
    }

    @Test
    void 문서에서_빠진_이미지는_지운다() {
        ImageEntity removed = image("550e8400-e29b-41d4-a716-446655440000", "doc-1");
        given(imageRepository.findByCreateTimeBefore(any())).willReturn(List.of(removed));
        given(documentRepository.findById("doc-1")).willReturn(Optional.of(
                document("[{\"type\":\"paragraph\",\"data\":{\"text\":\"이미지를 뺐다\"}}]")));

        maintenanceService.cleanupOrphans();

        verify(imageStorageService).delete(removed);
    }

    @Test
    void 한_건이_실패해도_나머지는_계속_치운다() {
        ImageEntity failing = image("img-1", null);
        ImageEntity next = image("img-2", null);
        given(imageRepository.findByCreateTimeBefore(any())).willReturn(List.of(failing, next));
        org.mockito.BDDMockito.willThrow(new RuntimeException("스토리지 장애"))
                .given(imageStorageService).delete(failing);

        maintenanceService.cleanupOrphans();

        verify(imageStorageService).delete(next);
    }

    // --- 보류 분석 재시도 ---

    @Test
    void 문서가_쓰는_보류_이미지는_다시_분석한다() {
        ImageEntity pending = image("550e8400-e29b-41d4-a716-446655440000", "doc-1");
        given(imageRepository.findPendingAnalysis()).willReturn(List.of(pending));
        given(documentRepository.findById("doc-1")).willReturn(Optional.of(
                document("[{\"type\":\"image\",\"data\":{\"url\":\"/api/images/550e8400-e29b-41d4-a716-446655440000\"}}]")));

        maintenanceService.retryPendingAnalysis();

        verify(imageAnalysisService).analyze("550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    void 아무도_쓰지_않는_보류_이미지는_분석하지_않는다() {
        given(imageRepository.findPendingAnalysis()).willReturn(List.of(image("img-1", null)));

        maintenanceService.retryPendingAnalysis();

        verify(imageAnalysisService, never()).analyze(any());
    }

    private ImageAnalysisQuotaEntity usedQuota(int used) {
        ImageAnalysisQuotaEntity quota = new ImageAnalysisQuotaEntity(workspaceId, LocalDate.now());
        for (int i = 0; i < used; i++) {
            quota.increase();
        }
        return quota;
    }

    private ImageEntity image(String imageId, String documentId) {
        ImageEntity image = new ImageEntity(
                imageId, workspaceId, "images/ws/" + imageId + ".png",
                "image/png", imageId + ".png", 100L, "hash-" + imageId, "user-1");
        if (documentId != null) {
            image.linkToDocumentIfAbsent(documentId);
        }
        return image;
    }

    private DocumentEntity document(String blocks) {
        return new DocumentEntity(
                "doc-1", workspaceId, null, "문서", blocks, "", "hash", 1, "user-1");
    }
}
