package com.fixlog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** 워크스페이스 범위의 분류 태그. 문서에 N:M으로 붙는다 (FR-LBL-001). */
@Entity
@Table(
        name = "label",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_label_name", columnNames = {"workspace_id", "label_name"})
)
public class LabelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workspace_id", columnDefinition = "uuid", nullable = false)
    private UUID workspaceId;

    @Column(name = "label_name", length = 50, nullable = false)
    private String labelName;

    @Column(name = "create_at", updatable = false)
    private Instant createAt;

    protected LabelEntity() {
    }

    public LabelEntity(UUID workspaceId, String labelName) {
        this.workspaceId = workspaceId;
        this.labelName = labelName;
        this.createAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getLabelName() {
        return labelName;
    }

    public Instant getCreateAt() {
        return createAt;
    }
}
