package com.fixlog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "apj_image")
public class ImageEntity {

    @Id
    @Column(name = "image_id", length = 100)
    private String imageId;

    @Column(name = "workspace_id", columnDefinition = "uuid", nullable = false)
    private UUID workspaceId;

    @Column(name = "object_name", length = 500, nullable = false)
    private String objectName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "original_name", length = 255)
    private String originalName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "create_user", length = 100)
    private String createUser;

    @Column(name = "create_time", updatable = false)
    private Instant createTime;

    protected ImageEntity() {
    }

    public ImageEntity(String imageId, UUID workspaceId, String objectName,
                       String contentType, String originalName, Long fileSize, String createUser) {
        this.imageId = imageId;
        this.workspaceId = workspaceId;
        this.objectName = objectName;
        this.contentType = contentType;
        this.originalName = originalName;
        this.fileSize = fileSize;
        this.createUser = createUser;
        this.createTime = Instant.now();
    }

    public String getImageId() { return imageId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getObjectName() { return objectName; }
    public String getContentType() { return contentType; }
    public String getOriginalName() { return originalName; }
    public Long getFileSize() { return fileSize; }
    public String getCreateUser() { return createUser; }
    public Instant getCreateTime() { return createTime; }
}
