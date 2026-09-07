package com.fixlog.domain.model;

/**
 * 권한 판정 결과. 한 번의 부모 체인 순회로 결과와 출처가 함께 나온다.
 *
 * @param effect       최종 접근 결과
 * @param source       결과가 결정된 방식
 * @param sourceNodeId 결과를 결정한 노드. 운영권 단축 판정이면 null
 */
public record AccessDecision(AccessEffect effect, AccessSource source, String sourceNodeId) {

    public static AccessDecision allowedByAdmin() {
        return new AccessDecision(AccessEffect.ALLOW, AccessSource.ADMIN, null);
    }

    /** 워크스페이스 멤버가 아닌 사용자. 판정 대상 트리에 진입하지 못한다. */
    public static AccessDecision deniedAsNonMember() {
        return new AccessDecision(AccessEffect.DENY, AccessSource.DEFAULT, null);
    }

    public boolean isAllowed() {
        return effect.isAllowed();
    }
}
