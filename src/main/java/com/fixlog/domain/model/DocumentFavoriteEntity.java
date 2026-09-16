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

/** 사용자별 문서 즐겨찾기. */
@Entity
@Table(
        name = "document_favorite",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_document_favorite", columnNames = {"document_id", "user_id"})
)
public class DocumentFavoriteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "document_id", length = 100, nullable = false)
    private String documentId;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Column(name = "create_at", updatable = false, nullable = false)
    private Instant createAt;

    protected DocumentFavoriteEntity() {
    }

    public DocumentFavoriteEntity(String documentId, UUID userId) {
        this.documentId = documentId;
        this.userId = userId;
        this.createAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getDocumentId() { return documentId; }
    public UUID getUserId() { return userId; }
    public Instant getCreateAt() { return createAt; }
}
