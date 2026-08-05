package com.fixlog.application.repository;

import com.fixlog.domain.model.SecurityPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SecurityPolicyRepository extends JpaRepository<SecurityPolicyEntity, UUID> {
}
