package com.fixlog.domain.model;

/**
 * 권한 레코드의 명시적 의도 (FR-PRM-001).
 *
 * <p>DENY는 상속 체인과 Base Access를 모두 우선하여 접근을 차단한다.
 * ALLOW는 대상 리소스에 대한 접근을 허용한다.
 */
public enum PermissionType {
    ALLOW,
    DENY
}
