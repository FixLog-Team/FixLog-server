package com.fixlog.domain.model;

/** 권한이 걸리는 대상. 폴더 권한은 하위 폴더·문서로 상속된다 (FR-PRM-006). */
public enum ResourceType {
    FOLDER,
    DOCUMENT
}
