package com.fixlog.application.repository;

import com.fixlog.domain.model.DocumentLabelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentLabelRepository extends JpaRepository<DocumentLabelEntity, UUID> {

    List<DocumentLabelEntity> findByDocumentId(String documentId);

    List<DocumentLabelEntity> findByLabelId(UUID labelId);

    Optional<DocumentLabelEntity> findByDocumentIdAndLabelId(String documentId, UUID labelId);

    void deleteByDocumentId(String documentId);

    void deleteByLabelId(UUID labelId);
}
