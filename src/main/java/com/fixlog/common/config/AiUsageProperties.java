package com.fixlog.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 한도와 단가표 (I-02, I-03 확정 사항).
 *
 * <p>한도는 <b>워크스페이스당 월 토큰 수</b>로 센다. 호출 횟수로 세면 긴 문서 요약 한 번과
 * 한 줄 요약 한 번이 같은 값으로 취급되어 실제 비용과 어긋난다.
 *
 * <p>단가표는 설정 파일에 둔다. 변경이 드물고 배포로 반영하면 충분하며, 집계 시점 단가를
 * 기록에 박아 두므로 표가 바뀌어도 과거 비용은 흔들리지 않는다.
 */
@ConfigurationProperties(prefix = "fixlog.ai")
public class AiUsageProperties {

    /** 무료 계층의 워크스페이스당 월 토큰 한도. */
    private long freeMonthlyTokenLimit = 200_000L;

    /** 무료 계층에 쓰는 모델. */
    private String freeModel = "gemini-3-flash-preview";

    /** 모델명 → 1,000 토큰당 단가. */
    private Map<String, ModelPrice> pricing = new HashMap<>();

    public long getFreeMonthlyTokenLimit() {
        return freeMonthlyTokenLimit;
    }

    public void setFreeMonthlyTokenLimit(long freeMonthlyTokenLimit) {
        this.freeMonthlyTokenLimit = freeMonthlyTokenLimit;
    }

    public String getFreeModel() {
        return freeModel;
    }

    public void setFreeModel(String freeModel) {
        this.freeModel = freeModel;
    }

    public Map<String, ModelPrice> getPricing() {
        return pricing;
    }

    public void setPricing(Map<String, ModelPrice> pricing) {
        this.pricing = pricing;
    }

    /** 표에 없는 모델은 0원으로 본다. 비용을 임의로 지어내지 않는다. */
    public ModelPrice priceOf(String model) {
        return pricing.getOrDefault(model, new ModelPrice());
    }

    public static class ModelPrice {

        private BigDecimal input = BigDecimal.ZERO;
        private BigDecimal output = BigDecimal.ZERO;

        public BigDecimal getInput() {
            return input;
        }

        public void setInput(BigDecimal input) {
            this.input = input;
        }

        public BigDecimal getOutput() {
            return output;
        }

        public void setOutput(BigDecimal output) {
            this.output = output;
        }
    }
}
