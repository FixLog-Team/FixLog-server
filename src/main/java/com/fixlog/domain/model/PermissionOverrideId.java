package com.fixlog.domain.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * {@link PermissionOverrideEntity}의 복합 키.
 * 키가 (노드, 사용자)이므로 같은 레벨에서의 권한 충돌이 원천적으로 발생하지 않는다.
 */
public class PermissionOverrideId implements Serializable {

    private String nodeId;
    private UUID userId;

    protected PermissionOverrideId() {
    }

    public PermissionOverrideId(String nodeId, UUID userId) {
        this.nodeId = nodeId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PermissionOverrideId other)) return false;
        return Objects.equals(nodeId, other.nodeId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, userId);
    }
}
