package com.fixlog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 워크스페이스 보안 정책 (FR-SEC-001).
 *
 * <p><b>개별 권한보다 위에 있다.</b> 권한이 다운로드를 허용해도 정책이 금지하면 금지된다.
 * 관리자 특권보다도 위다 — 정책은 관리자가 스스로에게 건 제약이기 때문이다 (FR-SEC-002).
 */
@Entity
@Table(name = "security_policy")
public class SecurityPolicyEntity {

    /** 워크스페이스당 하나뿐이므로 워크스페이스 ID를 그대로 키로 쓴다. */
    @Id
    @Column(name = "workspace_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID workspaceId;

    /**
     * 구성원 간 공유 허용 여부.
     *
     * <p>기획서의 "외부 공유"는 공개 링크(2차)를 전제로 한 항목이라 지금은 강제할 지점이 없다.
     * 대신 지금 의미가 있는 제약 — 문서를 만든 사람 밖으로 내보내지 못하게 하는 것 — 으로 정의한다.
     */
    @Column(name = "allow_sharing", nullable = false)
    private boolean allowSharing = true;

    @Column(name = "allow_download", nullable = false)
    private boolean allowDownload = true;

    @Column(name = "enforce_watermark", nullable = false)
    private boolean enforceWatermark = false;

    @Column(name = "audit_retention_days", nullable = false)
    private int auditRetentionDays = 365;

    @Column(name = "trash_retention_days", nullable = false)
    private int trashRetentionDays = 30;

    @Column(name = "create_at", updatable = false)
    private Instant createAt;

    @Column(name = "update_at")
    private Instant updateAt;

    protected SecurityPolicyEntity() {
    }

    public SecurityPolicyEntity(UUID workspaceId) {
        this.workspaceId = workspaceId;
        this.createAt = Instant.now();
        this.updateAt = Instant.now();
    }

    public void update(Boolean allowSharing, Boolean allowDownload, Boolean enforceWatermark,
                       Integer auditRetentionDays, Integer trashRetentionDays) {
        if (allowSharing != null) {
            this.allowSharing = allowSharing;
        }
        if (allowDownload != null) {
            this.allowDownload = allowDownload;
        }
        if (enforceWatermark != null) {
            this.enforceWatermark = enforceWatermark;
        }
        if (auditRetentionDays != null) {
            this.auditRetentionDays = auditRetentionDays;
        }
        if (trashRetentionDays != null) {
            this.trashRetentionDays = trashRetentionDays;
        }
        this.updateAt = Instant.now();
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public boolean isAllowSharing() {
        return allowSharing;
    }

    public boolean isAllowDownload() {
        return allowDownload;
    }

    public boolean isEnforceWatermark() {
        return enforceWatermark;
    }

    public int getAuditRetentionDays() {
        return auditRetentionDays;
    }

    public int getTrashRetentionDays() {
        return trashRetentionDays;
    }

    public Instant getCreateAt() {
        return createAt;
    }

    public Instant getUpdateAt() {
        return updateAt;
    }
}
