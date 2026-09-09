package com.fixlog.application.service;

import com.fixlog.application.repository.AuditLogRepository;
import com.fixlog.domain.model.AuditAction;
import com.fixlog.domain.model.AuditLogEntity;
import com.fixlog.domain.model.AuditResult;
import com.fixlog.domain.model.PrincipalType;
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

    /**
     * 권한 변경 기록. 누가(actor) 누구에게(target) 무엇을(detail) 했는지 남긴다.
     *
     * <p>접근 기록과 달리 {@code viaAdmin}을 쓰지 않는다. 그 값은 "관리자 특권으로 열람했다"를
     * 뜻하는데, 권한 변경은 애초에 권한이 있어야 가능하므로 구분할 의미가 없다.
     *
     * @param resourceType 대상 폴더·문서. 역할 변경처럼 리소스가 없는 사건이면 null
     * @param detail       변경 전후 요약. 예: {@code "ALLOW → DENY"}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordChange(UUID workspaceId, UUID actorUserId, AuditAction action,
                             ResourceType resourceType, String resourceId,
                             PrincipalType targetPrincipalType, UUID targetPrincipalId,
                             String detail) {
        if (workspaceId == null || actorUserId == null) {
            return;
        }
        try {
            auditLogRepository.save(new AuditLogEntity(
                    workspaceId, actorUserId, action, resourceType, resourceId,
                    targetPrincipalType, targetPrincipalId, detail,
                    AuditResult.ALLOWED, false));
        } catch (Exception e) {
            log.error("권한 변경 감사 로그 적재 실패: action={}, target={}:{}, detail={}",
                    action, targetPrincipalType, targetPrincipalId, detail, e);
        }
    }
}
