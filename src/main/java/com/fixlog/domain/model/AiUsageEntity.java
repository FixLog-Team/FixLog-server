package com.fixlog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * AI 호출 한 건의 사용 기록 (FR-AI-005).
 *
 * <p>비용은 <b>집계 시점 단가를 함께 저장</b>한다. 단가표만 참조하면 나중에 요금이 바뀔 때
 * 과거 기록의 비용이 소급해서 달라진다. 이미 청구했거나 보고한 값이 흔들리면 안 된다 (FR-AI-007).
 */
@Entity
@Table(name = "ai_usage")
public class AiUsageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workspace_id", columnDefinition = "uuid", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", length = 20, nullable = false)
    private AiModelTier tier;

    @Column(name = "model", length = 100, nullable = false)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    /** 1,000 토큰당 단가. 산정 근거를 기록에 박아 둔다. */
    @Column(name = "input_price_per_1k", precision = 12, scale = 6)
    private BigDecimal inputPricePer1k;

    @Column(name = "output_price_per_1k", precision = 12, scale = 6)
    private BigDecimal outputPricePer1k;

    @Column(name = "cost", precision = 14, scale = 6)
    private BigDecimal cost;

    /** 실패한 호출도 남긴다. 실패가 몰리는 구간을 못 보면 원인 추적이 안 된다. */
    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "create_at", updatable = false)
    private Instant createAt;

    protected AiUsageEntity() {
    }

    public AiUsageEntity(UUID workspaceId, UUID userId, AiModelTier tier, String model,
                         long inputTokens, long outputTokens,
                         BigDecimal inputPricePer1k, BigDecimal outputPricePer1k,
                         boolean success) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.tier = tier;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.inputPricePer1k = inputPricePer1k;
        this.outputPricePer1k = outputPricePer1k;
        this.cost = calculateCost(inputTokens, outputTokens, inputPricePer1k, outputPricePer1k);
        this.success = success;
        this.createAt = Instant.now();
    }

    private static BigDecimal calculateCost(long inputTokens, long outputTokens,
                                            BigDecimal inputPricePer1k, BigDecimal outputPricePer1k) {
        BigDecimal thousand = BigDecimal.valueOf(1000);
        // 나누어떨어지지 않는 단가가 흔하므로 반올림 자리를 명시한다. 생략하면 예외가 난다.
        BigDecimal input = price(inputPricePer1k).multiply(BigDecimal.valueOf(inputTokens))
                .divide(thousand, 6, java.math.RoundingMode.HALF_UP);
        BigDecimal output = price(outputPricePer1k).multiply(BigDecimal.valueOf(outputTokens))
                .divide(thousand, 6, java.math.RoundingMode.HALF_UP);
        return input.add(output);
    }

    private static BigDecimal price(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public long totalTokens() {
        return inputTokens + outputTokens;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getUserId() {
        return userId;
    }

    public AiModelTier getTier() {
        return tier;
    }

    public String getModel() {
        return model;
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }

    public BigDecimal getInputPricePer1k() {
        return inputPricePer1k;
    }

    public BigDecimal getOutputPricePer1k() {
        return outputPricePer1k;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public boolean isSuccess() {
        return success;
    }

    public Instant getCreateAt() {
        return createAt;
    }
}
