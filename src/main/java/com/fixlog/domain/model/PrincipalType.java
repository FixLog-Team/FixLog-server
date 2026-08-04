package com.fixlog.domain.model;

/** 권한을 부여받는 주체. 같은 레벨에서는 USER가 GROUP보다 우선한다 (FR-PRM-005). */
public enum PrincipalType {
    USER,
    GROUP
}
