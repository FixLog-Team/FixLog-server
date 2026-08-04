package com.fixlog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** 그룹 소속. 한 사용자는 같은 워크스페이스 안에서 여러 그룹에 속할 수 있다. */
@Entity
@Table(
        name = "workspace_group_member",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_workspace_group_member", columnNames = {"group_id", "user_id"})
)
public class GroupMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "group_id", columnDefinition = "uuid", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Column(name = "create_at", updatable = false)
    private Instant createAt;

    protected GroupMemberEntity() {
    }

    public GroupMemberEntity(UUID groupId, UUID userId) {
        this.groupId = groupId;
        this.userId = userId;
        this.createAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getCreateAt() {
        return createAt;
    }
}
