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

/** 문서-라벨 매핑. */
@Entity
@Table(
        name = "document_label",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_document_label", columnNames = {"document_id", "label_id"})
)
public class DocumentLabelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "document_id", length = 100, nullable = false)
    private String documentId;

    @Column(name = "label_id", columnDefinition = "uuid", nullable = false)
    private UUID labelId;

    @Column(name = "create_user", length = 100)
    private String createUser;

    @Column(name = "create_at", updatable = false)
    private Instant createAt;

    protected DocumentLabelEntity() {
    }

    public DocumentLabelEntity(String documentId, UUID labelId, String createUser) {
        this.documentId = documentId;
        this.labelId = labelId;
        this.createUser = createUser;
        this.createAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public UUID getLabelId() {
        return labelId;
    }

    public String getCreateUser() {
        return createUser;
    }

    public Instant getCreateAt() {
        return createAt;
    }
}
