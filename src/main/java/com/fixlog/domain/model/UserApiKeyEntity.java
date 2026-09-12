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
 * 사용자가 등록한 고성능 모델 자격증명 (FR-AI-002, 003).
 *
 * <p>원문은 암호화해 보관하고 <b>어떤 응답에도 내보내지 않는다.</b> 화면에서 어떤 키를
 * 등록했는지 알아볼 수 있도록 마지막 네 자리만 따로 저장한다.
 */
@Entity
@Table(
        name = "user_api_key",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_api_key_provider", columnNames = {"user_id", "provider"})
)
public class UserApiKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Column(name = "provider", length = 30, nullable = false)
    private String provider;

    @Column(name = "encrypted_key", length = 1000, nullable = false)
    private String encryptedKey;

    /** 마지막 네 자리. 키를 식별하기 위한 최소한의 힌트다. */
    @Column(name = "key_hint", length = 8, nullable = false)
    private String keyHint;

    @Column(name = "create_at", updatable = false)
    private Instant createAt;

    @Column(name = "update_at")
    private Instant updateAt;

    protected UserApiKeyEntity() {
    }

    public UserApiKeyEntity(UUID userId, String provider, String encryptedKey, String keyHint) {
        this.userId = userId;
        this.provider = provider;
        this.encryptedKey = encryptedKey;
        this.keyHint = keyHint;
        this.createAt = Instant.now();
        this.updateAt = Instant.now();
    }

    public void replace(String encryptedKey, String keyHint) {
        this.encryptedKey = encryptedKey;
        this.keyHint = keyHint;
        this.updateAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }

    public String getEncryptedKey() {
        return encryptedKey;
    }

    public String getKeyHint() {
        return keyHint;
    }

    public Instant getCreateAt() {
        return createAt;
    }

    public Instant getUpdateAt() {
        return updateAt;
    }

    /** 로그·예외 메시지에 실려도 원문이 새지 않도록 한다. */
    @Override
    public String toString() {
        return "UserApiKeyEntity{provider='" + provider + "', keyHint='" + keyHint + "'}";
    }
}
