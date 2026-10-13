package com.fixlog.application.service;

import com.fixlog.application.repository.ImageRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.ImageEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml"
    );
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10MB

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ImageRepository imageRepository;

    @Value("${oci.storage.bucket}")
    private String bucketName;

    @Value("${oci.storage.presign-expiry-minutes:60}")
    private long presignExpiryMinutes;

    public ImageStorageService(S3Client s3Client, S3Presigner s3Presigner, ImageRepository imageRepository) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.imageRepository = imageRepository;
    }

    public ImageEntity upload(UUID workspaceId, String userId, MultipartFile file) {
        validateFile(file);

        String imageId = UUID.randomUUID().toString();
        String extension = extractExtension(file.getOriginalFilename());
        String objectName = "images/" + workspaceId + "/" + imageId + extension;

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectName)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            throw new BusinessException(Code.UNKNOWN, "이미지 업로드 중 오류가 발생했습니다.");
        } catch (Exception e) {
            throw new BusinessException(Code.UNKNOWN, "스토리지 업로드 실패: " + e.getMessage());
        }

        ImageEntity image = new ImageEntity(
                imageId, workspaceId, objectName,
                file.getContentType(), file.getOriginalFilename(), file.getSize(), userId
        );
        return imageRepository.save(image);
    }

    /** 만료 시간이 있는 Presigned URL을 생성한다. 버킷은 private 상태를 유지한다. */
    public String generatePresignedUrl(String imageId) {
        ImageEntity image = imageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "이미지를 찾을 수 없습니다."));

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignExpiryMinutes))
                .getObjectRequest(req -> req
                        .bucket(bucketName)
                        .key(image.getObjectName()))
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(Code.INVALID_REQUEST, "파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(Code.INVALID_REQUEST, "파일 크기는 10MB를 초과할 수 없습니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(Code.INVALID_REQUEST, "지원하지 않는 이미지 형식입니다. (jpeg, png, gif, webp, svg)");
        }
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}
