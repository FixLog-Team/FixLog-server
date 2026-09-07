package com.fixlog.domain.model;

/**
 * 권한 판정 대상이 되는 노드의 종류.
 * 부모 체인은 DOCUMENT → FOLDER → ... → WORKSPACE 순으로 올라간다.
 */
public enum NodeType {
    WORKSPACE,
    FOLDER,
    DOCUMENT
}
