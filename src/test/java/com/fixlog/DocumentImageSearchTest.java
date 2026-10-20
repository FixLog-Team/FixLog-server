package com.fixlog;

import com.fixlog.application.event.ImageAnalyzedEvent;
import com.fixlog.application.event.ImageUploadedEvent;
import com.fixlog.application.repository.ImageRepository;
import com.fixlog.application.service.DocumentImageAnalyzer;
import com.fixlog.application.service.DocumentImageReferenceExtractor;
import com.fixlog.application.service.DocumentTextExtractor;
import com.fixlog.application.service.ImageAnalysisQuotaService;
import com.fixlog.application.service.ImageAnalysisService;
import com.fixlog.application.service.ImageStorageService;
import com.fixlog.domain.model.ImageAnalysisStatus;
import com.fixlog.domain.model.ImageEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 문서 이미지 검색: 블록에서 이미지 찾기, 비전 분석 진행, 검색용 텍스트 추출.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentImageSearchTest {

    private final DocumentImageReferenceExtractor referenceExtractor = new DocumentImageReferenceExtractor();
    private final DocumentTextExtractor textExtractor = new DocumentTextExtractor();

    @Mock ImageRepository imageRepository;
    @Mock ImageStorageService imageStorageService;
    @Mock DocumentImageAnalyzer analyzer;
    @Mock ImageAnalysisQuotaService quotaService;
    @Mock ApplicationEventPublisher eventPublisher;

    private ImageAnalysisService analysisService;

    @BeforeEach
    void setUp() {
        analysisService = new ImageAnalysisService(
                imageRepository, imageStorageService, analyzer, quotaService, eventPublisher);
        given(imageRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(quotaService.tryConsume(any())).willReturn(true);
    }

    // --- 블록에서 이미지 참조 찾기 ---

    @Test
    void blocks에서_이미지_id를_뽑는다() {
        String id = UUID.randomUUID().toString();
        String blocks = """
                [{"type":"image","data":{"url":"https://fixlog.art/fixlog/api/images/%s"}}]
                """.formatted(id);

        assertThat(referenceExtractor.extract(blocks)).containsExactly(id);
    }

    @Test
    void 같은_이미지를_여러_번_넣어도_한_번만_다룬다() {
        String id = UUID.randomUUID().toString();
        String blocks = """
                [{"type":"image","data":{"url":"/api/images/%s"}},
                 {"type":"image","props":{"url":"/api/images/%s"}}]
                """.formatted(id, id);

        assertThat(referenceExtractor.extract(blocks)).containsExactly(id);
    }

    @Test
    void 이미지가_없거나_깨진_blocks면_빈_목록() {
        assertThat(referenceExtractor.extract(null)).isEmpty();
        assertThat(referenceExtractor.extract("")).isEmpty();
        assertThat(referenceExtractor.extract("{{깨진 JSON")).isEmpty();
        assertThat(referenceExtractor.extract("""
                [{"type":"paragraph","data":{"text":"이미지 없음"}}]""")).isEmpty();
    }

    @Test
    void 외부_URL_이미지는_대상이_아니다() {
        String blocks = """
                [{"type":"image","data":{"url":"https://example.com/photo.png"}}]""";

        assertThat(referenceExtractor.extract(blocks)).isEmpty();
    }

    // --- 분석 진행 ---

    @Test
    void 분석이_끝나면_결과를_저장하고_재색인을_알린다() {
        ImageEntity image = image("img-1", "doc-1");
        given(imageRepository.findById("img-1")).willReturn(Optional.of(image));
        given(analyzer.canAnalyze("image/png")).willReturn(true);
        given(imageStorageService.download(image)).willReturn(new byte[10]);
        given(analyzer.analyze(any(), any())).willReturn("[글자] 500 에러\n[종류] 차트\n[의미] 오후에 급증");

        analysisService.analyze("img-1");

        assertThat(image.getAnalysisStatus()).isEqualTo(ImageAnalysisStatus.COMPLETED);
        assertThat(image.hasSearchableText()).isTrue();
        verify(eventPublisher).publishEvent(any(ImageAnalyzedEvent.class));
    }

    @Test
    void 아직_문서에_붙지_않았으면_재색인을_알리지_않는다() {
        ImageEntity image = image("img-1", null);
        given(imageRepository.findById("img-1")).willReturn(Optional.of(image));
        given(analyzer.canAnalyze("image/png")).willReturn(true);
        given(imageStorageService.download(image)).willReturn(new byte[10]);
        given(analyzer.analyze(any(), any())).willReturn("[글자] 없음\n[종류] 사진\n[의미] 설명");

        analysisService.analyze("img-1");

        assertThat(image.getAnalysisStatus()).isEqualTo(ImageAnalysisStatus.COMPLETED);
        // 곧 이어질 문서 저장이 색인하므로 여기서 알릴 필요가 없다
        verify(eventPublisher, never()).publishEvent(any(ImageAnalyzedEvent.class));
    }

    @Test
    void 비전_모델이_못_읽는_형식은_건너뛴다() {
        ImageEntity image = new ImageEntity(
                "img-1", UUID.randomUUID(), "images/ws/a.svg",
                "image/svg+xml", "a.svg", 10L, "hash", "user-1");
        given(imageRepository.findById("img-1")).willReturn(Optional.of(image));
        given(analyzer.canAnalyze("image/svg+xml")).willReturn(false);

        analysisService.analyze("img-1");

        assertThat(image.getAnalysisStatus()).isEqualTo(ImageAnalysisStatus.SKIPPED);
        assertThat(image.hasSearchableText()).isFalse();
        verify(imageStorageService, never()).download(any());
    }

    @Test
    void 분석에_실패해도_이미지는_남고_상태만_기록된다() {
        ImageEntity image = image("img-1", "doc-1");
        given(imageRepository.findById("img-1")).willReturn(Optional.of(image));
        given(analyzer.canAnalyze("image/png")).willReturn(true);
        given(imageStorageService.download(image)).willThrow(new RuntimeException("스토리지 장애"));

        analysisService.analyze("img-1");

        assertThat(image.getAnalysisStatus()).isEqualTo(ImageAnalysisStatus.FAILED);
        assertThat(image.getAnalysisError()).contains("스토리지 장애");
        assertThat(image.getObjectName()).isNotBlank();
        verify(eventPublisher, never()).publishEvent(any(ImageAnalyzedEvent.class));
    }

    @Test
    void 이미_분석된_이미지는_다시_분석하지_않는다() {
        ImageEntity image = image("img-1", "doc-1");
        image.completeAnalysis("이미 분석됨");
        given(imageRepository.findById("img-1")).willReturn(Optional.of(image));

        analysisService.analyze("img-1");

        verify(analyzer, never()).analyze(any(), any());
    }

    @Test
    void 일일_상한에_걸리면_분석을_보류하고_PENDING으로_남긴다() {
        ImageEntity image = image("img-1", "doc-1");
        given(imageRepository.findById("img-1")).willReturn(Optional.of(image));
        given(analyzer.canAnalyze("image/png")).willReturn(true);
        given(quotaService.tryConsume(any())).willReturn(false);

        analysisService.analyze("img-1");

        // 실패가 아니라 보류다. 다음 날 정비 작업이 다시 집어가야 하므로 상태를 바꾸지 않는다
        assertThat(image.getAnalysisStatus()).isEqualTo(ImageAnalysisStatus.PENDING);
        verify(analyzer, never()).analyze(any(), any());
        verify(imageStorageService, never()).download(any());
    }

    @Test
    void 업로드_이벤트가_분석을_시작시킨다() {
        ImageEntity image = image("img-1", null);
        given(imageRepository.findById("img-1")).willReturn(Optional.of(image));
        given(analyzer.canAnalyze("image/png")).willReturn(true);
        given(imageStorageService.download(image)).willReturn(new byte[10]);
        given(analyzer.analyze(any(), any())).willReturn("[글자] 없음\n[종류] 사진\n[의미] 설명");

        analysisService.onImageUploaded(new ImageUploadedEvent("img-1"));

        verify(analyzer).analyze(any(), any());
    }

    // --- 본문 텍스트 추출 공백 ---

    @Test
    void 이미지_캡션이_본문_텍스트에_포함된다() {
        String blocks = """
                [{"type":"image","data":{"url":"/api/images/x","caption":"배포 후 응답 지연 그래프"}}]""";

        assertThat(textExtractor.extract(blocks)).isEqualTo("배포 후 응답 지연 그래프");
    }

    @Test
    void 표_내용이_본문_텍스트에_포함된다() {
        String blocks = """
                [{"type":"table","data":{"content":[["항목","값"],["타임아웃","30초"]]}}]""";

        String text = textExtractor.extract(blocks);

        assertThat(text).contains("항목 값").contains("타임아웃 30초");
    }

    @Test
    void 캡션이_없는_이미지는_빈_줄을_남기지_않는다() {
        String blocks = """
                [{"type":"paragraph","data":{"text":"앞 문단"}},
                 {"type":"image","data":{"url":"/api/images/x"}},
                 {"type":"paragraph","data":{"text":"뒤 문단"}}]""";

        assertThat(textExtractor.extract(blocks)).isEqualTo("앞 문단\n뒤 문단");
    }

    private ImageEntity image(String imageId, String documentId) {
        ImageEntity image = new ImageEntity(
                imageId, UUID.randomUUID(), "images/ws/" + imageId + ".png",
                "image/png", imageId + ".png", 100L, "hash-" + imageId, "user-1");
        if (documentId != null) {
            image.linkToDocumentIfAbsent(documentId);
        }
        return image;
    }
}
