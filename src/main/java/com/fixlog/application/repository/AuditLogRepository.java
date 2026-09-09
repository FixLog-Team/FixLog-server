package com.fixlog.application.repository;

import com.fixlog.domain.model.AuditAction;
import com.fixlog.domain.model.AuditLogEntity;
import com.fixlog.domain.model.AuditResult;
import com.fixlog.domain.model.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /**
     * 관리자 콘솔의 필터 조회 (FR-AUD-005). 넘기지 않은 조건은 무시한다.
     *
     * <p>워크스페이스는 항상 건다 — 조건을 비워도 전역이 되지 않게 하기 위함이다 (D2).
     *
     * <p>{@code actorUserId}는 행위자, {@code targetPrincipalId}는 권한을 받거나 잃은 대상이다.
     * "이 사람이 무엇을 했나"와 "이 사람에게 무슨 권한이 오갔나"는 다른 질문이라 조건을 나눈다.
     */
    @Query("""
            select a from AuditLogEntity a
            where a.workspaceId = :workspaceId
              and (:actorUserId is null or a.actorUserId = :actorUserId)
              and (:targetPrincipalId is null or a.targetPrincipalId = :targetPrincipalId)
              and (:action is null or a.action = :action)
              and (:result is null or a.result = :result)
              and (:from is null or a.createAt >= :from)
              and (:to is null or a.createAt < :to)
            order by a.createAt desc
            """)
    List<AuditLogEntity> search(@Param("workspaceId") UUID workspaceId,
                                @Param("actorUserId") UUID actorUserId,
                                @Param("targetPrincipalId") UUID targetPrincipalId,
                                @Param("action") AuditAction action,
                                @Param("result") AuditResult result,
                                @Param("from") Instant from,
                                @Param("to") Instant to);
}
