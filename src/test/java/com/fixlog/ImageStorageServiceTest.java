package com.fixlog;

import com.fixlog.application.event.ImageUploadedEvent;
import com.fixlog.application.repository.ImageRepository;
import com.fixlog.application.service.ImageStorageService;
import com.fixlog.application.service.PermissionEvaluator;
import com.fixlog.application.service.WorkspaceContext;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.ImageAnalysisStatus;
import com.fixlog.domain.model.ImageEntity;
import com.fixlog.domain.model.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImageStorageServiceTest {

    @Mock S3Client s3Client;
    @Mock S3Presigner s3Presigner;
    @Mock ImageRepository imageRepository;
    @Mock WorkspaceContext workspaceContext;
    @Mock PermissionEvaluator permissionEvaluator;
    @Mock ApplicationEventPublisher eventPublisher;

    ImageStorageService service;

    private final UUID uploaderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ImageStorageService(
                s3Client, s3Presigner, imageRepository, workspaceContext, permissionEvaluator, eventPublisher);
        ReflectionTestUtils.setField(service, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(service, "presignExpiryMinutes", 60L);

        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());
        given(imageRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(workspaceContext.requireCurrentUserId()).willReturn(uploaderId);
    }

    // --- 업로드 ---

    @Test
    void upload_성공() {
        UUID workspaceId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[100]);

        ImageEntity result = service.upload(workspaceId, "user-1", file);

        assertThat(result.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(result.getContentType()).isEqualTo("image/png");
        assertThat(result.getObjectName()).startsWith("images/" + workspaceId + "/").endsWith(".png");
        assertThat(result.getContentHash()).hasSize(64);
        assertThat(result.getAnalysisStatus()).isEqualTo(ImageAnalysisStatus.PENDING);
        verify(imageRepository).save(any());
        verify(eventPublisher).publishEvent(any(ImageUploadedEvent.class));
    }

    @Test
    void 같은_내용의_이미지는_기존_분석을_재사용하고_다시_분석하지_않는다() {
        ImageEntity analyzed = image("old", UUID.randomUUID(), null);
        analyzed.completeAnalysis("[글자] NullPointerException\n[종류] 에러 화면\n[의미] 예외 스택");
        given(imageRepository.findFirstByContentHashAndAnalysisStatusOrderByAnalyzedAtAsc(
                any(), any())).willReturn(Optional.of(analyzed));

        MockMultipartFile file = new MockMultipartFile("file", "same.png", "image/png", new byte[100]);
        ImageEntity result = service.upload(UUID.randomUUID(), "user-1", file);

        assertThat(result.getAnalysisStatus()).isEqualTo(ImageAnalysisStatus.COMPLETED);
        assertThat(result.getAnalysisText()).contains("NullPointerException");
        verify(eventPublisher, never()).publishEvent(any(ImageUploadedEvent.class));
    }

    @Test
    void upload_허용되지_않는_형식이면_예외() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[100]);

        assertThatThrownBy(() -> service.upload(UUID.randomUUID(), "user-1", file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(Code.INVALID_REQUEST);
    }

    @Test
    void upload_10MB_초과하면_예외() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.png", "image/png", new byte[11 * 1024 * 1024]);

        assertThatThrownBy(() -> service.upload(UUID.randomUUID(), "user-1", file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(Code.INVALID_REQUEST);
    }

    @Test
    void upload_빈_파일이면_예외() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.upload(UUID.randomUUID(), "user-1", file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(Code.INVALID_REQUEST);
    }

    // --- 조회 권한 ---

    @Test
    void 문서에_붙은_이미지는_문서_조회_권한이_있으면_볼_수_있다() throws MalformedURLException {
        ImageEntity image = image("img-1", UUID.randomUUID(), "doc-1");
        given(imageRepository.findById("img-1")).willReturn(Optional.of(image));
        given(permissionEvaluator.evaluate(ResourceType.DOCUMENT, "doc-1"))
                .willReturn(new PermissionEvaluator.Decision(true, true, false, true));
        givenPresignedUrl();

        assertThat(service.generatePresignedUrl("img-1")).contains("X-Amz-Signature");
    }

    @Test
    void 문서_조회_권한이_없으면_서명_URL을_받을_수_없다() {
        ImageEntity image = image("img-1", UUID.randomUUID(), "doc-1");
        given(imageRepository.findById("img-1")).willReturn(Optional.of(image));
        given(permissionEvaluator.evaluate(ResourceType.DOCUMENT, "doc-1"))
                .willReturn(new PermissionEvaluator.Decision(false, false, false, false));

        assertThatThrownBy(() -> service.generatePresignedUrl("img-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(Code.FORBIDDEN);
    }

    @Test
    void 아직_문서에_붙지_않은_이미지는_올린_본인만_볼_수_있다() throws MalformedURLException {
        ImageEntity image = image("img-1", UUID.randomUUID(), null);
        given(imageRepository.findById("img-1")).willReturn(Optional.of(image));
        givenPresignedUrl();

        assertThat(service.generatePresignedUrl("img-1")).contains("X-Amz-Signature");
    }

    @Test
    void 문서에_붙지_않은_남의_이미지는_볼_수_없다() {
        ImageEntity image = new ImageEntity(
                "img-1", UUID.randomUUID(), "images/ws/img.png",
                "image/png", "img.png", 100L, "hash", UUID.randomUUID().toString());
        given(imageRepository.findById("img-1")).willReturn(Optional.of(image));

        assertThatThrownBy(() -> service.generatePresignedUrl("img-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(Code.FORBIDDEN);
    }

    @Test
    void generatePresignedUrl_없는_이미지면_예외() {
        given(imageRepository.findById(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.generatePresignedUrl("not-exist"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(Code.NOT_FOUND);
    }

    private ImageEntity image(String imageId, UUID workspaceId, String documentId) {
        ImageEntity image = new ImageEntity(
                imageId, workspaceId, "images/ws/img.png",
                "image/png", "img.png", 100L, "hash-" + imageId, uploaderId.toString());
        if (documentId != null) {
            image.linkToDocumentIfAbsent(documentId);
        }
        return image;
    }

    private void givenPresignedUrl() throws MalformedURLException {
        PresignedGetObjectRequest presigned = org.mockito.Mockito.mock(PresignedGetObjectRequest.class);
        given(presigned.url()).willReturn(new URL("https://oci.example.com/signed-url?X-Amz-Signature=abc"));
        given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).willReturn(presigned);
    }
}
