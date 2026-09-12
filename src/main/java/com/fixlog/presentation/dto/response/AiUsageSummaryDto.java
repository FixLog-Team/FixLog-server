package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.AiUsageEntity;

import java.math.BigDecimal;
import java.util.List;

/** 이번 달 사용량 요약 (FR-AI-008). */
public record AiUsageSummaryDto(
        long freeTokensUsed,
        long freeTokenLimit,
        long freeTokensRemaining,
        long totalCalls,
        BigDecimal totalCost
) {
    public static AiUsageSummaryDto of(long used, long limit, List<AiUsageEntity> usages) {
        BigDecimal cost = usages.stream()
                .map(AiUsageEntity::getCost)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AiUsageSummaryDto(used, limit, Math.max(0, limit - used), usages.size(), cost);
    }
}
