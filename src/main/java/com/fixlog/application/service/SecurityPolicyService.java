package com.fixlog.application.service;

import com.fixlog.application.repository.SecurityPolicyRepository;
import com.fixlog.domain.model.SecurityPolicyEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 워크스페이스 보안 정책 (FR-SEC-001, 005).
 *
 * <p>정책이 없으면 기본값으로 본다. 없는 워크스페이스마다 행을 미리 만들어 두면
 * 워크스페이스 생성 경로가 정책을 알아야 하고, 새 정책 항목이 늘 때마다 백필이 필요해진다.
 */
@Service
public class SecurityPolicyService {

    private final SecurityPolicyRepository policyRepository;
    private final WorkspaceService workspaceService;

    public SecurityPolicyService(SecurityPolicyRepository policyRepository,
                                 WorkspaceService workspaceService) {
        this.policyRepository = policyRepository;
        this.workspaceService = workspaceService;
    }

    /** 판정 경로에서 쓰는 조회. 저장하지 않고 기본값 객체를 돌려준다. */
    @Transactional(readOnly = true)
    public SecurityPolicyEntity effectivePolicy(UUID workspaceId) {
        return policyRepository.findById(workspaceId)
                .orElseGet(() -> new SecurityPolicyEntity(workspaceId));
    }

    @Transactional(readOnly = true)
    public SecurityPolicyEntity view(UUID workspaceId) {
        workspaceService.requireMembership(workspaceId);
        return effectivePolicy(workspaceId);
    }

    /** 정책 변경은 관리자만 (FR-SEC-005). */
    @Transactional
    public SecurityPolicyEntity update(UUID workspaceId, Boolean allowSharing, Boolean allowDownload,
                                       Boolean enforceWatermark, Integer auditRetentionDays,
                                       Integer trashRetentionDays) {
        workspaceService.requireAdmin(workspaceId);

        SecurityPolicyEntity policy = policyRepository.findById(workspaceId)
                .orElseGet(() -> new SecurityPolicyEntity(workspaceId));
        policy.update(allowSharing, allowDownload, enforceWatermark,
                auditRetentionDays, trashRetentionDays);
        return policyRepository.save(policy);
    }
}
