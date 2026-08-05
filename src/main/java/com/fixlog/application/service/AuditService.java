package com.fixlog.application.service;

import com.fixlog.application.repository.AuditLogRepository;
import com.fixlog.domain.model.AuditAction;
import com.fixlog.domain.model.AuditLogEntity;
import com.fixlog.domain.model.AuditResult;
import com.fixlog.domain.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 감사 로그 적재 (FR-AUD-001~004).
 *
 * <p><b>본 작업과 다른 트랜잭션에 쓴다.</b> 거부된 접근은 예외로 끝나면서 본 트랜잭션이
 * 롤백되는데, 같은 트랜잭션에 기록하면 "막혔다"는 기록도 함께 사라진다. 정작 남겨야 할 것이
 * 사라지는 셈이다. 조회 트랜잭션이 읽기 전용인 것도 같은 이유로 분리가 필요하다.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID workspaceId, UUID actorUserId, AuditAction action,
                       ResourceType resourceType, String resourceId,
                       AuditResult result, boolean viaAdmin) {
        if (workspaceId == null || actorUserId == null) {
            return;
        }
        try {
            auditLogRepository.save(new AuditLogEntity(
                    workspaceId, actorUserId, action, resourceType, resourceId, result, viaAdmin));
        } catch (Exception e) {
            // 기록 실패가 본 작업을 막지는 않는다. 대신 조용히 넘어가지 않고 남긴다.
            log.error("감사 로그 적재 실패: action={}, resource={}:{}, result={}",
                    action, resourceType, resourceId, result, e);
        }
    }
}
