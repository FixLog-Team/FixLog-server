package com.fixlog.domain.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * 특정 노드에 대한 사용자 개별 설정. 부모 체인 순회 중 가장 먼저 만나는 이 값이 결과를 확정한다.
 *
 * <p>{@code nodeType}과 {@code workspaceId}는 키가 아니라 관리 화면의 목록 조회를 위한 보조 컬럼이다.
 */
@Entity
@Table(name = "apj_permission_override")
@IdClass(PermissionOverrideId.class)
public class PermissionOverrideEntity {

    @Id
    @Column(name = "node_id", length = 100)
    private String nodeId;

    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", length = 10, nullable = false)
    private NodeType nodeType;

    @Column(name = "workspace_id", length = 100, nullable = false)
    private String workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "effect", length = 10, nullable = false)
    private AccessEffect effect;

    @Column(name = "create_time", updatable = false)
    private Instant createTime;

    @Column(name = "update_user", length = 100)
    private String updateUser;

    @Column(name = "update_time")
    private Instant updateTime;

    protected PermissionOverrideEntity() {
    }

    public PermissionOverrideEntity(String nodeId, UUID userId, NodeType nodeType,
                                    String workspaceId, AccessEffect effect, String updateUser) {
        this.nodeId = nodeId;
        this.userId = userId;
        this.nodeType = nodeType;
        this.workspaceId = workspaceId;
        this.effect = effect;
        this.createTime = Instant.now();
        this.updateUser = updateUser;
        this.updateTime = Instant.now();
    }

    public void changeEffect(AccessEffect effect, String updateUser) {
        this.effect = effect;
        this.updateUser = updateUser;
        this.updateTime = Instant.now();
    }

    public String getNodeId() {
        return nodeId;
    }

    public UUID getUserId() {
        return userId;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public AccessEffect getEffect() {
        return effect;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public String getUpdateUser() {
        return updateUser;
    }

    public Instant getUpdateTime() {
        return updateTime;
    }
}
