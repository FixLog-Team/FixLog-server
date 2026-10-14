package com.fixlog.presentation.controller.image;

import com.fixlog.application.service.ImageStorageService;
import com.fixlog.application.service.WorkspaceContext;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.domain.model.ImageEntity;
import com.fixlog.presentation.dto.response.ImageUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/images")
@Tag(name = "Image")
public class ImageController {

    /**
     * 브라우저 캐시 수명.
     *
     * <p>짧게 잡는 이유는 권한 때문이다. 길게 두면 공유가 끊긴 뒤에도 그만큼 캐시로 계속 보인다.
     * 만료 뒤에는 ETag 재검증이 돌아 권한을 다시 확인하고, 내용이 같으면 304로 끝나 전송은 없다.
     */
    private static final Duration CACHE_MAX_AGE = Duration.ofMinutes(5);

    private final ImageStorageService imageStorageService;
    private final WorkspaceContext workspaceContext;

    public ImageController(ImageStorageService imageStorageService, WorkspaceContext workspaceContext) {
        this.imageStorageService = imageStorageService;
        this.workspaceContext = workspaceContext;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "uploadImage")
    public Response upload(@RequestParam("file") MultipartFile file) {
        UUID workspaceId = workspaceContext.requireCurrentWorkspaceId();
        String userId = workspaceContext.requireCurrentUserId().toString();

        ImageEntity image = imageStorageService.upload(workspaceId, userId, file);

        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/images/{imageId}")
                .buildAndExpand(image.getImageId())
                .toUriString();

        return DataResponse.success(ImageUploadResponse.of(image, url));
    }

    /**
     * 이미지 원본을 서버가 직접 내려준다.
     *
     * <p>서명 URL로 리다이렉트하지 않는 이유는 두 가지다. 서명 URL은 한 번 나가면 만료 전까지
     * 권한을 회수할 수 없고, 스토리지가 다른 origin이라 브라우저가 받으려면 버킷 CORS 설정이 필요하다.
     * 서버가 대신 읽어 보내면 요청이 전부 같은 origin에 머물고 권한도 매 요청 확인된다.
     */
    @GetMapping("/{imageId}")
    @Operation(operationId = "getImage")
    public ResponseEntity<byte[]> getImage(
            @PathVariable String imageId,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        // 바이트를 읽기 전에 권한부터 본다. 캐시가 유효하면 스토리지를 건드리지 않고 끝난다.
        ImageEntity image = imageStorageService.requireReadable(imageId);
        String eTag = eTagOf(image);

        if (eTag != null && eTag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(CacheControl.maxAge(CACHE_MAX_AGE).cachePrivate())
                    .eTag(eTag)
                    .build();
        }

        byte[] content = imageStorageService.download(image);

        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(CACHE_MAX_AGE).cachePrivate())
                .contentType(mediaTypeOf(image))
                .contentLength(content.length);
        if (eTag != null) {
            response.eTag(eTag);
        }
        return response.body(content);
    }

    /**
     * 내용 해시를 ETag로 쓴다. 같은 이미지는 바이트가 바뀌지 않으므로 재검증이 항상 304로 끝난다.
     * 이번 기능 이전에 올라온 이미지는 해시가 없어 ETag 없이 내려간다.
     */
    private String eTagOf(ImageEntity image) {
        String hash = image.getContentHash();
        return hash == null || hash.isBlank() ? null : "\"" + hash + "\"";
    }

    private MediaType mediaTypeOf(ImageEntity image) {
        try {
            return MediaType.parseMediaType(image.getContentType());
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
