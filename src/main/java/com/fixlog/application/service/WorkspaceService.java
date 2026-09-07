package com.fixlog.application.service;

import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.application.repository.WorkspaceRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.security.SecurityUtil;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.WorkspaceEntity;
import com.fixlog.domain.model.WorkspaceMemberEntity;
import com.fixlog.domain.model.WorkspaceRole;
import com.fixlog.presentation.dto.response.WorkspaceDto;
import com.fixlog.presentation.dto.response.WorkspaceMemberDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WorkspaceService {

    private static final Integer ACTIVE = 1;

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final PermissionResolver permissionResolver;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
                            WorkspaceMemberRepository memberRepository,
                            UserRepository userRepository,
                            PermissionResolver permissionResolver) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.permissionResolver = permissionResolver;
    }

    /**
     * 사용자의 개인 워크스페이스를 보장한다. 로그인마다 호출되므로 멱등해야 한다.
     * 권한 트리는 루트가 없으면 성립하지 않으므로, 모든 사용자는 최소 하나의 워크스페이스를 갖는다.
     */
    @Transactional
    public WorkspaceEntity ensurePersonalWorkspace(UserEntity user) {
        return memberRepository
                .findFirstByUserIdAndRoleOrderByCreateTimeAsc(user.getUserId(), WorkspaceRole.OWNER)
                .flatMap(member -> workspaceRepository.findByWorkspaceIdAndUsable(member.getWorkspaceId(), ACTIVE))
                .orElseGet(() -> createPersonalWorkspace(user));
    }

    private WorkspaceEntity createPersonalWorkspace(UserEntity user) {
        String workspaceId = UUID.randomUUID().toString();
        String creator = user.getUserId().toString();

        WorkspaceEntity workspace = workspaceRepository.save(
                new WorkspaceEntity(workspaceId, user.getUserName() + " 워크스페이스", creator));
        memberRepository.save(
                new WorkspaceMemberEntity(workspaceId, user.getUserId(), WorkspaceRole.OWNER, creator));
        return workspace;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceDto> myWorkspaces() {
        UUID userId = UUID.fromString(SecurityUtil.requireCurrentUserId());

        List<WorkspaceMemberEntity> memberships = memberRepository.findByUserId(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }

        Map<String, WorkspaceRole> roleByWorkspaceId = new LinkedHashMap<>();
        for (WorkspaceMemberEntity membership : memberships) {
            roleByWorkspaceId.put(membership.getWorkspaceId(), membership.getRole());
        }

        List<WorkspaceDto> result = new ArrayList<>();
        for (WorkspaceEntity workspace :
                workspaceRepository.findByWorkspaceIdInAndUsable(roleByWorkspaceId.keySet(), ACTIVE)) {
            result.add(WorkspaceDto.from(workspace, roleByWorkspaceId.get(workspace.getWorkspaceId())));
        }
        return result;
    }

    /**
     * 워크스페이스 멤버 목록. 소속되지 않은 워크스페이스는 존재 자체를 알리지 않는다.
     */
    @Transactional(readOnly = true)
    public List<WorkspaceMemberDto> members(String workspaceId) {
        String userId = SecurityUtil.requireCurrentUserId();
        String resolved = permissionResolver.resolveWorkspaceId(userId, workspaceId);

        List<WorkspaceMemberEntity> members = memberRepository.findByWorkspaceIdOrderByCreateTimeAsc(resolved);
        Map<UUID, UserEntity> usersById = userRepository
                .findAllById(members.stream().map(WorkspaceMemberEntity::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(UserEntity::getUserId, Function.identity()));

        return members.stream()
                .map(member -> WorkspaceMemberDto.of(member, usersById.get(member.getUserId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceEntity getWorkspace(String workspaceId) {
        return workspaceRepository.findByWorkspaceIdAndUsable(workspaceId, ACTIVE)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "워크스페이스를 찾을 수 없습니다."));
    }
}
