package com.fixlog.application.repository;

import com.fixlog.domain.model.UserOauthEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserOauthRepository extends JpaRepository<UserOauthEntity, UUID> {
    Optional<UserOauthEntity> findByProviderAndProviderId(String provider, String providerId);
}
