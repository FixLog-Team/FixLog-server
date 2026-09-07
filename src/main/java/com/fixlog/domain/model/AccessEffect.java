package com.fixlog.domain.model;

/**
 * 권한 판정의 최종 결과. 1차에서 ALLOW는 읽기와 편집을 모두 포함한다.
 */
public enum AccessEffect {
    ALLOW,
    DENY;

    public boolean isAllowed() {
        return this == ALLOW;
    }
}
