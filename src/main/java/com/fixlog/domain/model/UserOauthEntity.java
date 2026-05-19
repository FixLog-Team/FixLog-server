package com.fixlog.domain.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "fixlog_user_oauth",
        schema = "public",
        uniqueConstraints = @UniqueConstraint(name = "uq_oauth_provider", columnNames = {"provider", "provider_id"})
)
public class UserOauthEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "provider", length = 30, nullable = false)
    private String provider;

    @Column(name = "provider_id", length = 255, nullable = false)
    private String providerId;

    @Column(name = "create_at", updatable = false)
    private Instant createAt;

    protected UserOauthEntity() {
    }

    public UserOauthEntity(UserEntity user, String provider, String providerId) {
        this.user = user;
        this.provider = provider;
        this.providerId = providerId;
        this.createAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UserEntity getUser() {
        return user;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderId() {
        return providerId;
    }
}
