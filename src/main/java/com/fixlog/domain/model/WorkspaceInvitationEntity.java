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
 * 이메일 기반 워크스페이스 초대.
 *
 * <p>초대는 토큰을 통해 수락/거절된다. 만료 이전에 수락되지 않으면 EXPIRED 처리된다.
 */
@Entity
@Table(name = "workspace_invitation")
public class WorkspaceInvitationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workspace_id", columnDefinition = "uuid", nullable = false)
    private UUID workspaceId;

    @Column(name = "email", length = 100, nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private WorkspaceRole role;

    @Column(name = "token", length = 64, nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private InvitationStatus status;

    @Column(name = "invited_by", columnDefinition = "uuid", nullable = false)
    private UUID invitedBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "create_at", updatable = false)
    private Instant createAt;

    @Column(name = "update_at")
    private Instant updateAt;

    protected WorkspaceInvitationEntity() {
    }

    public WorkspaceInvitationEntity(UUID workspaceId, String email, WorkspaceRole role,
                                     String token, UUID invitedBy, Instant expiresAt) {
        this.workspaceId = workspaceId;
        this.email = email;
        this.role = role;
        this.token = token;
        this.status = InvitationStatus.PENDING;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
        this.createAt = Instant.now();
        this.updateAt = Instant.now();
    }

    public void accept() {
        this.status = InvitationStatus.ACCEPTED;
        this.updateAt = Instant.now();
    }

    public void decline() {
        this.status = InvitationStatus.DECLINED;
        this.updateAt = Instant.now();
    }

    public void expire() {
        this.status = InvitationStatus.EXPIRED;
        this.updateAt = Instant.now();
    }

    public boolean isPending() {
        return status == InvitationStatus.PENDING;
    }

    public boolean isExpired() {
        return status == InvitationStatus.EXPIRED || Instant.now().isAfter(expiresAt);
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getEmail() { return email; }
    public WorkspaceRole getRole() { return role; }
    public String getToken() { return token; }
    public InvitationStatus getStatus() { return status; }
    public UUID getInvitedBy() { return invitedBy; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreateAt() { return createAt; }
    public Instant getUpdateAt() { return updateAt; }
}
