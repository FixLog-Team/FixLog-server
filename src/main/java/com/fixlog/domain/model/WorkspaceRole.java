package com.fixlog.domain.model;

/**
 * 워크스페이스 내 역할. 사용자의 속성이 아니라 (워크스페이스, 사용자) 쌍의 속성이므로
 * {@link UserEntity}가 아니라 {@link WorkspaceMemberEntity}에 붙는다.
 *
 * <p>우선순위: OWNER > ADMIN > MEMBER.
 * OWNER는 역할 변경과 워크스페이스 삭제가 가능한 최고 권한이다.
 */
public enum WorkspaceRole {
    OWNER,
    ADMIN,
    MEMBER
}
