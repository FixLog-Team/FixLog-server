package com.fixlog.domain.model;

/**
 * 접근의 결과.
 *
 * <p>{@code DENIED}를 남기는 이유는 "누가 무엇을 열어봤는가"만큼 "누가 무엇을 열려다 막혔는가"도
 * 사고 조사에 필요하기 때문이다 (FR-AUD-004).
 */
public enum AuditResult {
    ALLOWED,
    DENIED
}
