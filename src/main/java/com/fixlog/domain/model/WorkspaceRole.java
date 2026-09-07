package com.fixlog.domain.model;

/**
 * 워크스페이스 운영권. 콘텐츠 접근권({@link BaseAccess}, {@link AccessEffect})과는 독립된 축이다.
 */
public enum WorkspaceRole {
    OWNER,
    ADMIN,
    MEMBER;

    /**
     * 콘텐츠 권한을 관리할 수 있는 역할.
     * 이 역할은 자신에게 Allow를 부여할 수 있으므로 Deny가 무의미하다.
     * 따라서 권한 계산 첫 부분에서 ALLOW로 단축 판정된다.
     */
    public boolean canManagePermission() {
        return this == OWNER || this == ADMIN;
    }
}
