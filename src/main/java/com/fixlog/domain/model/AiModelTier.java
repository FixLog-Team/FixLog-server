package com.fixlog.domain.model;

/**
 * 모델 계층 (FR-AI-001).
 *
 * <p>비용을 누가 부담하는지가 갈린다. {@code FREE}는 서비스가 부담하므로 워크스페이스 한도로
 * 막고, {@code PREMIUM}은 사용자가 등록한 키로 호출하므로 한도를 걸지 않는다.
 */
public enum AiModelTier {
    FREE,
    PREMIUM
}
