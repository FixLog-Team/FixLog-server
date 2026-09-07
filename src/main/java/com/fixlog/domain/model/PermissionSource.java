package com.fixlog.domain.model;

/** 유효 권한의 출처. 관리자 콘솔의 Access 뷰에서 사용자에게 보여주는 값이다. */
public enum PermissionSource {
    WORKSPACE_DEFAULT,
    INHERITED,
    DIRECT
}
