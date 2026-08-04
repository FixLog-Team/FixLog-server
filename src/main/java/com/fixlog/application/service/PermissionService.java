package com.fixlog.application.service;

import com.fixlog.application.repository.GroupRepository;
import com.fixlog.application.repository.PermissionRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.PermissionAction;
import com.fixlog.domain.model.PermissionEntity;
import com.fixlog.domain.model.PermissionLevel;
import com.fixlog.domain.model.PrincipalType;
import com.fixlog.domain.model.ResourceType;
import com.fixlog.presentation.dto.response.PermissionDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 공유는 별도 개념이 아니라 <b>권한 레코드를 만드는 행위</b>다 (5.2).
 *
 * <p>따라서 이 서비스가 공유 API의 본체이며, 폴더 공유와 문서 공유는 대상 타입만 다르다.
 */
@Service
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final PermissionEvaluator permissionEvaluator;
    private final WorkspaceContext workspaceContext;

    public PermissionService(PermissionRepository permissionRepository,
                             WorkspaceMemberRepository workspaceMemberRepository,
                             GroupRepository groupRepository,
                             UserRepository userRepository,
                             PermissionEvaluator permissionEvaluator,
                             WorkspaceContext workspaceContext) {
        this.permissionRepository = permissionRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.permissionEvaluator = permissionEvaluator;
        this.workspaceContext = workspaceContext;
    }

    /**
     * 만든 사람에게 소유 권한을 준다.
     *
     * <p>이게 없으면 일반 구성원은 문서를 만드는 즉시 접근을 잃는다. 만들었다는 사실
     * ({@code create_user})은 접근 제어에 쓰이지 않기 때문이다 (FR-PRM-011).
     * 권한 판정을 거치지 않는 유일한 부여 경로이므로 생성 직후에만 호출한다.
     */
    @Transactional
    public PermissionEntity grantCreatorOwnership(UUID workspaceId, ResourceType resourceType,
                                                  String resourceId, UUID creatorId) {
        return permissionRepository.save(new PermissionEntity(
                workspaceId, PrincipalType.USER, creatorId,
                resourceType, resourceId,
                PermissionLevel.OWNER, true, creatorId));
    }

    /** 공유 = 권한 부여 (FR-SHR-001~003). 공유 설정은 OWNER만 할 수 있다. */
    @Transactional
    public PermissionEntity share(ResourceType resourceType, String resourceId,
                                  PrincipalType principalType, UUID principalId,
                                  PermissionLevel level, boolean canDownload) {
        permissionEvaluator.require(resourceType, resourceId, PermissionAction.SHARE);
        UUID workspaceId = permissionEvaluator.workspaceIdOf(resourceType, resourceId);
        UUID granter = workspaceContext.requireCurrentUserId();

        if (level == null) {
            throw new BusinessException(Code.INVALID_REQUEST, "권한 레벨은 필수입니다.");
        }
        requirePrincipalInWorkspace(workspaceId, principalType, principalId);

        // 같은 대상·주체에 이미 있으면 덮어쓴다. 중복 레코드가 생기면 판정이 흔들린다.
        return permissionRepository
                .findByResourceTypeAndResourceIdAndPrincipalTypeAndPrincipalId(
                        resourceType, resourceId, principalType, principalId)
                .map(existing -> {
                    existing.update(level, canDownload);
                    return permissionRepository.save(existing);
                })
                .orElseGet(() -> permissionRepository.save(new PermissionEntity(
                        workspaceId, principalType, principalId,
                        resourceType, resourceId, level, canDownload, granter)));
    }

    /** 공유 회수 (FR-SHR-004). */
    @Transactional
    public void revoke(ResourceType resourceType, String resourceId, UUID permissionId) {
        permissionEvaluator.require(resourceType, resourceId, PermissionAction.SHARE);

        PermissionEntity permission = permissionRepository.findById(permissionId)
                .filter(p -> p.getResourceType() == resourceType && p.getResourceId().equals(resourceId))
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "권한을 찾을 수 없습니다."));

        permissionRepository.delete(permission);
    }

    /** 이 리소스가 누구에게 공유돼 있는지. 공유를 설정할 수 있는 사람만 볼 수 있다. */
    @Transactional(readOnly = true)
    public java.util.List<PermissionDto> listFor(ResourceType resourceType, String resourceId) {
        permissionEvaluator.require(resourceType, resourceId, PermissionAction.SHARE);
        return permissionRepository.findByResourceTypeAndResourceId(resourceType, resourceId).stream()
                .map(p -> PermissionDto.of(p, principalNameOf(p)))
                .toList();
    }

    /** 공유 목록은 사람이 읽는 화면이므로 주체의 이름을 붙여 준다. */
    private String principalNameOf(PermissionEntity permission) {
        return switch (permission.getPrincipalType()) {
            case USER -> userRepository.findById(permission.getPrincipalId())
                    .map(u -> u.getUserName()).orElse(null);
            case GROUP -> groupRepository.findById(permission.getPrincipalId())
                    .map(g -> g.getGroupName()).orElse(null);
        };
    }

    /**
     * 공유 대상은 같은 워크스페이스의 구성원·그룹으로 한정된다 (FR-SHR-007).
     * 밖의 주체에게 권한을 주면 워크스페이스 경계가 무너진다.
     */
    private void requirePrincipalInWorkspace(UUID workspaceId, PrincipalType principalType, UUID principalId) {
        if (principalId == null) {
            throw new BusinessException(Code.INVALID_REQUEST, "공유 대상은 필수입니다.");
        }
        switch (principalType) {
            case USER -> {
                if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, principalId)) {
                    throw new BusinessException(Code.NOT_FOUND, "워크스페이스 구성원이 아닙니다.");
                }
            }
            case GROUP -> groupRepository.findByGroupIdAndWorkspaceId(principalId, workspaceId)
                    .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "그룹을 찾을 수 없습니다."));
        }
    }

    /** 이메일로 사용자를 찾아 공유한다. 초대와 같은 방식으로 대상을 지정하기 위한 편의 경로다. */
    @Transactional
    public PermissionEntity shareWithEmail(ResourceType resourceType, String resourceId,
                                           String email, PermissionLevel level, boolean canDownload) {
        if (email == null || email.isBlank()) {
            throw new BusinessException(Code.INVALID_REQUEST, "공유할 사용자의 이메일은 필수입니다.");
        }
        UUID userId = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "가입된 사용자를 찾을 수 없습니다."))
                .getUserId();
        return share(resourceType, resourceId, PrincipalType.USER, userId, level, canDownload);
    }
}
