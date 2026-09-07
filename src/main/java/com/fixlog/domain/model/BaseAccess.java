package com.fixlog.domain.model;

/**
 * 노드의 기본 접근 정책. 상속 여부와 확정값을 하나의 값으로 합친 3-state다.
 *
 * <ul>
 *   <li>{@code INHERIT} — 부모로 계속 올라간다.</li>
 *   <li>{@code ALLOW} / {@code DENY} — 여기서 상속을 끊고 값을 확정한다.</li>
 * </ul>
 *
 * 워크스페이스 루트는 상속할 부모가 없으므로 {@code INHERIT}이 될 수 없다.
 */
public enum BaseAccess {
    INHERIT,
    ALLOW,
    DENY;

    /** 여기서 상속이 끊기는지. INHERIT이 아니면 값이 확정된다. */
    public boolean isTerminal() {
        return this != INHERIT;
    }

    /** 확정된 정책을 접근 결과로 바꾼다. INHERIT에는 결과가 없다. */
    public AccessEffect toEffect() {
        return switch (this) {
            case ALLOW -> AccessEffect.ALLOW;
            case DENY -> AccessEffect.DENY;
            case INHERIT -> throw new IllegalStateException("INHERIT은 접근 결과로 변환할 수 없습니다.");
        };
    }
}
