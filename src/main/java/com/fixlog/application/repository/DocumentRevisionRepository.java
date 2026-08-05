package com.fixlog.application.repository;

import com.fixlog.domain.model.DocumentRevisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRevisionRepository extends JpaRepository<DocumentRevisionEntity, UUID> {

    List<DocumentRevisionEntity> findByDocumentIdOrderByRevisionNoDesc(String documentId);

    Optional<DocumentRevisionEntity> findByDocumentIdAndRevisionNo(String documentId, int revisionNo);

    Optional<DocumentRevisionEntity> findTopByDocumentIdOrderByRevisionNoDesc(String documentId);
}
