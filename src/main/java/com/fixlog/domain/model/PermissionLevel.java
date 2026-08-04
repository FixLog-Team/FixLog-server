package com.fixlog.domain.model;

/**
 * 권한 레벨 (FR-PRM-001).
 *
 * <p>다운로드는 이 레벨에 포함되지 않는다. "열람은 허용하되 반출은 금지"가 DRM 요구의 핵심이라
 * 별도 플래그({@code canDownload})로 분리한다 (FR-PRM-002).
 */
public enum PermissionLevel {

    VIEWER(1),
    EDITOR(2),
    OWNER(3);

    private final int strength;

    PermissionLevel(int strength) {
        this.strength = strength;
    }

    public boolean allows(PermissionAction action) {
        return switch (action) {
            case VIEW -> true;
            case EDIT -> strength >= EDITOR.strength;
            case SHARE, DELETE -> strength >= OWNER.strength;
        };
    }

    public boolean isAtLeast(PermissionLevel other) {
        return this.strength >= other.strength;
    }
}
