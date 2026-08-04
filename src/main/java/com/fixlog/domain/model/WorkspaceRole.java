package com.fixlog.domain.model;

/**
 * 워크스페이스 내 역할. 사용자의 속성이 아니라 (워크스페이스, 사용자) 쌍의 속성이므로
 * {@link UserEntity}가 아니라 {@link WorkspaceMemberEntity}에 붙는다.
 */
public enum WorkspaceRole {
    ADMIN,
    MEMBER
}
