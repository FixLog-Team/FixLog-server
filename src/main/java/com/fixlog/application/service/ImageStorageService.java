package com.fixlog.application.service;

import com.fixlog.application.event.ImageUploadedEvent;
import com.fixlog.application.repository.ImageRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.ImageAnalysisStatus;
import com.fixlog.domain.model.ImageEntity;
import com.fixlog.domain.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml"
    );
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10MB

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ImageRepository imageRepository;
    private final WorkspaceContext workspaceContext;
    private final PermissionEvaluator permissionEvaluator;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${oci.storage.bucket}")
    private String bucketName;

    @Value("${oci.storage.presign-expiry-minutes:60}")
    private long presignExpiryMinutes;

    public ImageStorageService(S3Client s3Client,
                               S3Presigner s3Presigner,
                               ImageRepository imageRepository,
                               WorkspaceContext workspaceContext,
                               PermissionEvaluator permissionEvaluator,
                               ApplicationEventPublisher eventPublisher) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.imageRepository = imageRepository;
        this.workspaceContext = workspaceContext;
        this.permissionEvaluator = permissionEvaluator;
        this.eventPublisher = eventPublisher;
    }

    public ImageEntity upload(UUID workspaceId, String userId, MultipartFile file) {
        validateFile(file);

        byte[] content = readContent(file);
        String contentHash = sha256(content);

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
                    RequestBody.fromBytes(content));
        } catch (Exception e) {
            throw new BusinessException(Code.UNKNOWN, "스토리지 업로드 실패: " + e.getMessage());
        }

        ImageEntity image = new ImageEntity(
                imageId, workspaceId, objectName,
                file.getContentType(), file.getOriginalFilename(), (long) content.length,
                contentHash, userId
        );
        reuseAnalysisOfSameContent(image, contentHash);
        ImageEntity saved = imageRepository.save(image);

        // 분석을 재사용했으면 비전 모델을 다시 부를 이유가 없다
        if (saved.needsAnalysis()) {
            startAnalysis(saved.getImageId());
        }
        return saved;
    }

    /**
     * 분석을 시작시킨다. 분석은 검색 품질을 올리는 부가 작업이므로, 여기서 무슨 일이 나든
     * 업로드 자체는 성공해야 한다. 분석되지 않은 이미지는 {@code PENDING}으로 남아
     * 나중에 다시 시도할 수 있다.
     */
    private void startAnalysis(String imageId) {
        try {
            eventPublisher.publishEvent(new ImageUploadedEvent(imageId));
        } catch (Exception e) {
            log.error("이미지 분석 시작 실패: imageId={}", imageId, e);
        }
    }

    /**
     * 같은 내용의 이미지가 이미 분석돼 있으면 그 결과를 그대로 가져온다.
     * 같은 스크린샷을 여러 문서에 붙이거나 문서를 복제해도 비전 모델 호출은 한 번으로 끝난다.
     */
    private void reuseAnalysisOfSameContent(ImageEntity image, String contentHash) {
        imageRepository
                .findFirstByContentHashAndAnalysisStatusOrderByAnalyzedAtAsc(
                        contentHash, ImageAnalysisStatus.COMPLETED)
                .ifPresent(analyzed -> image.completeAnalysis(analyzed.getAnalysisText()));
    }

    /**
     * 만료 시간이 있는 Presigned URL을 생성한다. 버킷은 private 상태를 유지한다.
     *
     * <p>서명 URL은 한 번 나가면 만료 전까지 누구나 쓸 수 있으므로, 발급 전에 반드시 접근 권한을 확인한다.
     */
    public String generatePresignedUrl(String imageId) {
        ImageEntity image = requireReadable(imageId);

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignExpiryMinutes))
                .getObjectRequest(req -> req
                        .bucket(bucketName)
                        .key(image.getObjectName()))
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    /** 분석에 쓸 원본 바이트를 읽는다. 서버 내부 호출이라 권한 검사 대상이 아니다. */
    public byte[] download(ImageEntity image) {
        try {
            return s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(image.getObjectName())
                            .build()
            ).asByteArray();
        } catch (Exception e) {
            throw new BusinessException(Code.NOT_FOUND, "이미지 원본을 읽지 못했습니다: " + e.getMessage());
        }
    }

    /**
     * 이미지를 볼 수 있는지 판정한다.
     *
     * <p>문서에 붙은 이미지는 <b>그 문서의 조회 권한을 그대로 따른다</b>. 공유가 끊기면 이미지도 함께 막힌다.
     * 아직 문서에 붙지 않은 이미지(업로드 직후)는 올린 본인만 볼 수 있다.
     *
     * <p>감사 로그는 남기지 않는다. 문서 하나에 이미지가 여러 장이면 열람 한 번에 기록이 장수만큼 쌓여
     * 실제 문서 열람 기록이 묻히기 때문이다.
     */
    private ImageEntity requireReadable(String imageId) {
        ImageEntity image = imageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "이미지를 찾을 수 없습니다."));

        if (image.getDocumentId() != null) {
            boolean allowed = permissionEvaluator
                    .evaluate(ResourceType.DOCUMENT, image.getDocumentId())
                    .allowed();
            if (!allowed) {
                throw new BusinessException(Code.FORBIDDEN, "이 이미지를 볼 권한이 없습니다.");
            }
            return image;
        }

        String currentUserId = workspaceContext.requireCurrentUserId().toString();
        if (!currentUserId.equals(image.getCreateUser())) {
            throw new BusinessException(Code.FORBIDDEN, "이 이미지를 볼 권한이 없습니다.");
        }
        return image;
    }

    /** 문서 저장 시 호출한다. 편집 권한은 호출부(문서 저장)에서 이미 확인된 상태다. */
    public void linkToDocument(ImageEntity image, String documentId) {
        image.linkToDocumentIfAbsent(documentId);
        imageRepository.save(image);
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

    private byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(Code.UNKNOWN, "이미지 업로드 중 오류가 발생했습니다.");
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}
