package com.fixlog.application.repository;

import com.fixlog.domain.model.ImageAnalysisQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ImageAnalysisQuotaRepository extends JpaRepository<ImageAnalysisQuotaEntity, UUID> {

    Optional<ImageAnalysisQuotaEntity> findByWorkspaceIdAndUsageDate(UUID workspaceId, LocalDate usageDate);
}
