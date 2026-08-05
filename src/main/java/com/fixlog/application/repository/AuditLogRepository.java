package com.fixlog.application.repository;

import com.fixlog.domain.model.AuditLogEntity;
import com.fixlog.domain.model.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * 감사 로그는 쌓고 읽기만 한다. 수정·삭제 경로를 열지 않는 것이 이 인터페이스의 요점이다
 * (NFR-003). {@code JpaRepository}가 delete를 물려주지만 애플리케이션 코드에서 호출하지 않는다.
 */
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

    List<AuditLogEntity> findByWorkspaceIdOrderByCreateAtDesc(UUID workspaceId);

    List<AuditLogEntity> findByResourceTypeAndResourceIdOrderByCreateAtDesc(
            ResourceType resourceType, String resourceId);
}
