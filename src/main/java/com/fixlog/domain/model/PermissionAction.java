package com.fixlog.domain.model;

/**
 * 판정 대상 행위.
 *
 * <p>다운로드가 없는 이유는 레벨이 아니라 별도 플래그로 판정하기 때문이다
 * ({@code PermissionDecision.canDownload}).
 */
public enum PermissionAction {
    VIEW,
    EDIT,
    SHARE,
    DELETE
}
