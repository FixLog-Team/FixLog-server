package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.ImageEntity;

public record ImageUploadResponse(
        String imageId,
        String url,
        String originalName,
        String contentType,
        long fileSize
) {
    public static ImageUploadResponse of(ImageEntity image, String url) {
        return new ImageUploadResponse(
                image.getImageId(),
                url,
                image.getOriginalName(),
                image.getContentType(),
                image.getFileSize() != null ? image.getFileSize() : 0L
        );
    }
}
