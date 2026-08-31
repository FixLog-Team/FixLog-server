package com.fixlog.application.repository;

import com.fixlog.domain.model.DocumentAiSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentAiSummaryRepository extends JpaRepository<DocumentAiSummaryEntity, String> {
}
