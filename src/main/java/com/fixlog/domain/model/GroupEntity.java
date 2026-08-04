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

/**
 * 워크스페이스 내 사용자 묶음. 권한 부여의 주체로 쓰인다. 워크스페이스 경계를 넘지 않는다.
 *
 * <p>테이블명이 {@code group}이 아닌 이유는 SQL 예약어이기 때문이다.
 */
@Entity
@Table(
        name = "workspace_group",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_workspace_group_name", columnNames = {"workspace_id", "group_name"})
)
public class GroupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "group_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID groupId;

    @Column(name = "workspace_id", columnDefinition = "uuid", nullable = false)
    private UUID workspaceId;

    @Column(name = "group_name", length = 100, nullable = false)
    private String groupName;

    @Column(name = "create_at", updatable = false)
    private Instant createAt;

    @Column(name = "update_at")
    private Instant updateAt;

    protected GroupEntity() {
    }

    public GroupEntity(UUID workspaceId, String groupName) {
        this.workspaceId = workspaceId;
        this.groupName = groupName;
        this.createAt = Instant.now();
        this.updateAt = Instant.now();
    }

    public void rename(String groupName) {
        this.groupName = groupName;
        this.updateAt = Instant.now();
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getGroupName() {
        return groupName;
    }

    public Instant getCreateAt() {
        return createAt;
    }

    public Instant getUpdateAt() {
        return updateAt;
    }
}
