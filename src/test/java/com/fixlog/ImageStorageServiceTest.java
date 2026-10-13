package com.fixlog;

import com.fixlog.application.repository.ImageRepository;
import com.fixlog.application.service.ImageStorageService;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.ImageEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ImageStorageServiceTest {

    @Mock S3Client s3Client;
    @Mock S3Presigner s3Presigner;
    @Mock ImageRepository imageRepository;

    ImageStorageService service;

    @BeforeEach
    void setUp() {
        service = new ImageStorageService(s3Client, s3Presigner, imageRepository);
        ReflectionTestUtils.setField(service, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(service, "presignExpiryMinutes", 60L);
    }

    @Test
    void upload_성공() {
        UUID workspaceId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[100]);

        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());
        given(imageRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ImageEntity result = service.upload(workspaceId, "user-1", file);

        assertThat(result.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(result.getContentType()).isEqualTo("image/png");
        assertThat(result.getObjectName()).startsWith("images/" + workspaceId + "/");
        assertThat(result.getObjectName()).endsWith(".png");
        verify(imageRepository).save(any());
    }

    @Test
    void upload_허용되지_않는_형식이면_예외() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", new byte[100]);

        assertThatThrownBy(() -> service.upload(UUID.randomUUID(), "user-1", file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(Code.INVALID_REQUEST);
    }

    @Test
    void upload_10MB_초과하면_예외() {
        byte[] bigFile = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.png", "image/png", bigFile);

        assertThatThrownBy(() -> service.upload(UUID.randomUUID(), "user-1", file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(Code.INVALID_REQUEST);
    }

    @Test
    void upload_빈_파일이면_예외() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.upload(UUID.randomUUID(), "user-1", file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(Code.INVALID_REQUEST);
    }

    @Test
    void generatePresignedUrl_성공() throws MalformedURLException {
        String imageId = UUID.randomUUID().toString();
        ImageEntity image = new ImageEntity(
                imageId, UUID.randomUUID(), "images/ws/img.png",
                "image/png", "img.png", 100L, "user-1");

        PresignedGetObjectRequest presigned = org.mockito.Mockito.mock(PresignedGetObjectRequest.class);
        given(presigned.url()).willReturn(new URL("https://oci.example.com/signed-url?X-Amz-Signature=abc"));
        given(imageRepository.findById(imageId)).willReturn(Optional.of(image));
        given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).willReturn(presigned);

        String url = service.generatePresignedUrl(imageId);

        assertThat(url).contains("X-Amz-Signature");
    }

    @Test
    void generatePresignedUrl_없는_이미지면_예외() {
        given(imageRepository.findById(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.generatePresignedUrl("not-exist"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(Code.NOT_FOUND);
    }
}
