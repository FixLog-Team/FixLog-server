package com.fixlog.presentation.controller.image;

import com.fixlog.application.service.ImageStorageService;
import com.fixlog.application.service.WorkspaceContext;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.domain.model.ImageEntity;
import com.fixlog.presentation.dto.response.ImageUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/images")
@Tag(name = "Image")
public class ImageController {

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
     * Presigned URL로 리다이렉트한다.
     * 버킷은 private 상태를 유지하고, 만료 시간이 있는 서명 URL을 OCI에서 직접 받는다.
     */
    @GetMapping("/{imageId}")
    @Operation(operationId = "getImage")
    public void getImage(@PathVariable String imageId, HttpServletResponse response) throws IOException {
        String presignedUrl = imageStorageService.generatePresignedUrl(imageId);
        response.sendRedirect(presignedUrl);
    }
}
