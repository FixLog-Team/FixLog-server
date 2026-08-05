package com.fixlog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 누가·언제·무엇을·어떻게 접근했는지의 기록.
 *
 * <p><b>append-only다.</b> 수정 메서드도, 삭제 API도 두지 않는다. 고칠 수 있는 감사 로그는
 * 사고가 났을 때 근거가 되지 못한다 (FR-AUD-002, NFR-003).
 */
@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workspace_id", columnDefinition = "uuid", nullable = false)
    private UUID workspaceId;

    @Column(name = "actor_user_id", columnDefinition = "uuid", nullable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 20, nullable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", length = 20, nullable = false)
    private ResourceType resourceType;

    @Column(name = "resource_id", length = 100, nullable = false)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 20, nullable = false)
    private AuditResult result;

    /** 관리자 특권으로 접근했는지. 권한을 받아서 본 것과 구분해야 조사에 쓸 수 있다. */
    @Column(name = "via_admin", nullable = false)
    private boolean viaAdmin;

    @Column(name = "create_at", updatable = false)
    private Instant createAt;

    protected AuditLogEntity() {
    }

    public AuditLogEntity(UUID workspaceId, UUID actorUserId, AuditAction action,
                          ResourceType resourceType, String resourceId,
                          AuditResult result, boolean viaAdmin) {
        this.workspaceId = workspaceId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.result = result;
        this.viaAdmin = viaAdmin;
        this.createAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public AuditAction getAction() {
        return action;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public AuditResult getResult() {
        return result;
    }

    public boolean isViaAdmin() {
        return viaAdmin;
    }

    public Instant getCreateAt() {
        return createAt;
    }
}
