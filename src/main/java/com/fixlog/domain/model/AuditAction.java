package com.fixlog.domain.model;

/** 감사 로그에 남기는 행위 (FR-AUD-001). */
public enum AuditAction {
    VIEW,
    DOWNLOAD,
    EDIT,
    DELETE,
    SHARE,
    RESTORE
}
