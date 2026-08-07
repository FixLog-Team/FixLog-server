package com.fixlog.common.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

/**
 * AI 호출별 토큰 사용량을 기능 단위로 로깅한다.
 * 어느 기능이 토큰을 얼마나 쓰는지 측정하여 비용 최적화의 근거 데이터로 사용한다.
 */
@Component
public class TokenUsageLogger {

    private static final Logger log = LoggerFactory.getLogger("AI_TOKEN_USAGE");

    public void logChat(String feature, ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            log.warn("feature={} usage=unavailable", feature);
            return;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            log.warn("feature={} usage=unavailable", feature);
            return;
        }
        log.info("feature={} promptTokens={} completionTokens={} totalTokens={}",
                feature, usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    public void logEmbedding(String feature, EmbeddingResponse response, int inputCount) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            log.warn("feature={} inputCount={} usage=unavailable", feature, inputCount);
            return;
        }
        Usage usage = response.getMetadata().getUsage();
        log.info("feature={} inputCount={} promptTokens={} totalTokens={}",
                feature, inputCount, usage.getPromptTokens(), usage.getTotalTokens());
    }
}
