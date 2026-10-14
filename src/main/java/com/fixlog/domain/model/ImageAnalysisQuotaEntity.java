package com.fixlog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 워크스페이스별 하루 비전 분석 호출 수.
 *
 * <p>분석 결과를 재사용한 건(내용 해시 일치)은 모델을 부르지 않으므로 세지 않는다.
 */
@Entity
@Table(name = "image_analysis_quota")
public class ImageAnalysisQuotaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workspace_id", columnDefinition = "uuid", nullable = false)
    private UUID workspaceId;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "analyzed_count", nullable = false)
    private int analyzedCount;

    @Column(name = "update_time")
    private Instant updateTime;

    protected ImageAnalysisQuotaEntity() {
    }

    public ImageAnalysisQuotaEntity(UUID workspaceId, LocalDate usageDate) {
        this.workspaceId = workspaceId;
        this.usageDate = usageDate;
        this.analyzedCount = 0;
        this.updateTime = Instant.now();
    }

    public void increase() {
        this.analyzedCount++;
        this.updateTime = Instant.now();
    }

    public boolean hasReached(int limit) {
        return analyzedCount >= limit;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public LocalDate getUsageDate() {
        return usageDate;
    }

    public int getAnalyzedCount() {
        return analyzedCount;
    }
}
