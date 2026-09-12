package com.fixlog.application.repository;

import com.fixlog.domain.model.UserApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserApiKeyRepository extends JpaRepository<UserApiKeyEntity, UUID> {

    List<UserApiKeyEntity> findByUserId(UUID userId);

    Optional<UserApiKeyEntity> findByUserIdAndProvider(UUID userId, String provider);
}
