package com.fixlog.domain.model;

/**
 * 권한이 어디서 결정되었는지. "왜 이 사람이 접근하지 못하는가"를 추적하기 위한 값이다.
 *
 * <ul>
 *   <li>{@code ADMIN} — 운영권으로 단축 판정됨</li>
 *   <li>{@code DIRECT} — 대상 노드에 직접 설정된 값</li>
 *   <li>{@code INHERITED} — 조상 노드의 사용자 개별 설정을 물려받음</li>
 *   <li>{@code DEFAULT} — 조상 노드의 기본 정책을 물려받음</li>
 * </ul>
 */
public enum AccessSource {
    ADMIN,
    DIRECT,
    INHERITED,
    DEFAULT
}
