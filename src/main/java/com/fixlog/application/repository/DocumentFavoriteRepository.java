package com.fixlog.application.repository;

import com.fixlog.domain.model.DocumentFavoriteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentFavoriteRepository extends JpaRepository<DocumentFavoriteEntity, UUID> {

    List<DocumentFavoriteEntity> findByUserIdOrderByCreateAtDesc(UUID userId);

    Optional<DocumentFavoriteEntity> findByDocumentIdAndUserId(String documentId, UUID userId);

    boolean existsByDocumentIdAndUserId(String documentId, UUID userId);

    void deleteByDocumentId(String documentId);
}
