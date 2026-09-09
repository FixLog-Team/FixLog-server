package com.fixlog.application.service;

import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.application.repository.WorkspaceRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.security.SecurityUtil;
import com.fixlog.domain.model.PermissionType;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.WorkspaceEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * 현재 요청이 어느 워크스페이스를 보고 있는지 해석한다.
 *
 * <p>클라이언트는 {@value #HEADER_NAME} 헤더로 워크스페이스를 지정한다. 헤더가 없으면
 * 개인 워크스페이스로 본다. 토큰에 담지 않는 이유는 전환할 때마다 재발급이 필요해지고,
 * 한 브라우저에서 두 워크스페이스를 동시에 열 수 없기 때문이다.
 */
@Component
public class WorkspaceContext {

    public static final String HEADER_NAME = "X-Workspace-Id";

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public WorkspaceContext(WorkspaceRepository workspaceRepository,
                            WorkspaceMemberRepository workspaceMemberRepository) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    public UserEntity requireCurrentUser() {
        UserEntity user = SecurityUtil.getCurrentUser();
        if (user == null) {
            throw new BusinessException(Code.UNAUTHORIZED, "인증 정보가 없습니다.");
        }
        return user;
    }

    public UUID requireCurrentUserId() {
        return requireCurrentUser().getUserId();
    }

    /**
     * 현재 워크스페이스. 요청자가 구성원이 아니면 존재 자체를 알리지 않기 위해 NOT_FOUND로 막는다.
     */
    public UUID requireCurrentWorkspaceId() {
        UUID userId = requireCurrentUserId();
        UUID workspaceId = requestedWorkspaceId().orElseGet(() -> personalWorkspaceId(userId));

        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new BusinessException(Code.NOT_FOUND, "워크스페이스를 찾을 수 없습니다.");
        }
        return workspaceId;
    }

    /**
     * 워크스페이스의 기본 접근 정책. 상속 체인이 루트까지 올라갔을 때 적용된다.
     *
     * <p>워크스페이스를 찾지 못하면 DENY다. 판정에서 불확실은 곧 누출이므로 닫는 방향으로 확정한다.
     */
    public PermissionType baseAccessOf(UUID workspaceId) {
        if (workspaceId == null) {
            return PermissionType.DENY;
        }
        return workspaceRepository.findById(workspaceId)
                .map(WorkspaceEntity::getBaseAccess)
                .orElse(PermissionType.DENY);
    }

    public WorkspaceEntity personalWorkspace(UUID userId) {
        return workspaceRepository.findByPersonalOwnerId(userId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "개인 워크스페이스를 찾을 수 없습니다."));
    }

    private UUID personalWorkspaceId(UUID userId) {
        return personalWorkspace(userId).getWorkspaceId();
    }

    /** 요청 컨텍스트 밖(스케줄러·테스트)에서는 헤더가 없으므로 비어 있는 값을 돌려준다. */
    private java.util.Optional<UUID> requestedWorkspaceId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return java.util.Optional.empty();
        }
        String raw = attributes.getRequest().getHeader(HEADER_NAME);
        if (raw == null || raw.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(Code.INVALID_REQUEST, HEADER_NAME + " 형식이 올바르지 않습니다.");
        }
    }
}
