package com.fixlog.application.service;

import com.fixlog.application.repository.AiUsageRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.config.AiUsageProperties;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.AiModelTier;
import com.fixlog.domain.model.AiUsageEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * AI 사용량 기록과 무료 한도 (FR-AI-005, 006, 007).
 */
@Service
public class AiUsageService {

    private final AiUsageRepository usageRepository;
    private final AiUsageProperties properties;

    public AiUsageService(AiUsageRepository usageRepository, AiUsageProperties properties) {
        this.usageRepository = usageRepository;
        this.properties = properties;
    }

    /** 이번 달 1일 0시(UTC). 한도는 달마다 초기화된다. */
    private Instant startOfMonth() {
        return YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    @Transactional(readOnly = true)
    public long freeTokensUsedThisMonth(UUID workspaceId) {
        return usageRepository.sumTokens(workspaceId, AiModelTier.FREE, startOfMonth());
    }

    public long freeMonthlyLimit() {
        return properties.getFreeMonthlyTokenLimit();
    }

    /**
     * 무료 계층 호출 전 한도를 확인한다 (FR-AI-006).
     *
     * <p>고성능 계층은 사용자가 자기 키로 부르므로 여기서 막지 않는다.
     */
    @Transactional(readOnly = true)
    public void requireFreeQuota(UUID workspaceId) {
        long used = freeTokensUsedThisMonth(workspaceId);
        long limit = freeMonthlyLimit();
        if (used >= limit) {
            throw new BusinessException(Code.FORBIDDEN,
                    "이번 달 무료 AI 사용량을 모두 사용했습니다. (%,d / %,d 토큰)".formatted(used, limit));
        }
    }

    /**
     * 호출 결과를 기록한다.
     *
     * <p>본 작업과 다른 트랜잭션에 쓴다. 호출이 실패해 예외로 끝나도 "얼마나 썼는지"는 남아야
     * 하고, 같은 트랜잭션이면 함께 롤백되기 때문이다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiUsageEntity record(UUID workspaceId, UUID userId, AiModelTier tier, String model,
                                long inputTokens, long outputTokens, boolean success) {
        AiUsageProperties.ModelPrice price = properties.priceOf(model);
        return usageRepository.save(new AiUsageEntity(
                workspaceId, userId, tier, model,
                inputTokens, outputTokens,
                price.getInput(), price.getOutput(), success));
    }

    @Transactional(readOnly = true)
    public List<AiUsageEntity> usageThisMonth(UUID workspaceId) {
        return usageRepository.findByWorkspaceIdAndCreateAtGreaterThanEqualOrderByCreateAtDesc(
                workspaceId, startOfMonth());
    }
}
