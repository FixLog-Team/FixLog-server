package com.fixlog.application.service;

import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.application.repository.WorkspaceRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.WorkspaceEntity;
import com.fixlog.domain.model.WorkspaceMemberEntity;
import com.fixlog.domain.model.WorkspaceRole;
import com.fixlog.presentation.dto.response.WorkspaceDto;
import com.fixlog.presentation.dto.response.WorkspaceMemberDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final WorkspaceContext workspaceContext;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
                            WorkspaceMemberRepository memberRepository,
                            UserRepository userRepository,
                            WorkspaceContext workspaceContext) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.workspaceContext = workspaceContext;
    }

    /**
     * 개인 워크스페이스를 보장한다 (FR-WS-002). 가입 경로에서 호출된다.
     * 이미 있으면 그대로 돌려주므로 재로그인해도 중복 생성되지 않는다.
     */
    @Transactional
    public WorkspaceEntity ensurePersonalWorkspace(UserEntity user) {
        return workspaceRepository.findByPersonalOwnerId(user.getUserId())
                .orElseGet(() -> {
                    WorkspaceEntity workspace = workspaceRepository.save(WorkspaceEntity.personalFor(user));
                    memberRepository.save(new WorkspaceMemberEntity(
                            workspace.getWorkspaceId(), user.getUserId(), WorkspaceRole.ADMIN));
                    return workspace;
                });
    }

    /** 누구나 워크스페이스를 만들 수 있고, 만든 사람이 관리자가 된다 (FR-WS-001). */
    @Transactional
    public WorkspaceEntity create(String workspaceName) {
        if (workspaceName == null || workspaceName.isBlank()) {
            throw new BusinessException(Code.INVALID_REQUEST, "워크스페이스 이름은 필수입니다.");
        }
        UUID userId = workspaceContext.requireCurrentUserId();

        WorkspaceEntity workspace = workspaceRepository.save(WorkspaceEntity.shared(workspaceName.trim()));
        memberRepository.save(new WorkspaceMemberEntity(
                workspace.getWorkspaceId(), userId, WorkspaceRole.ADMIN));
        return workspace;
    }

    /** 내가 속한 워크스페이스 목록. 워크스페이스마다 역할이 다르다 (FR-WS-004, FR-WS-012). */
    @Transactional(readOnly = true)
    public List<WorkspaceDto> myWorkspaces() {
        UUID userId = workspaceContext.requireCurrentUserId();
        List<WorkspaceMemberEntity> memberships = memberRepository.findByUserId(userId);

        Map<UUID, WorkspaceEntity> workspaces = workspaceRepository
                .findAllById(memberships.stream().map(WorkspaceMemberEntity::getWorkspaceId).toList())
                .stream()
                .collect(Collectors.toMap(WorkspaceEntity::getWorkspaceId, Function.identity()));

        return memberships.stream()
                .map(m -> WorkspaceDto.of(workspaces.get(m.getWorkspaceId()), m.getRole()))
                .filter(dto -> dto != null)
                .sorted(Comparator.comparing(WorkspaceDto::createAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMemberDto> members(UUID workspaceId) {
        requireMembership(workspaceId);

        List<WorkspaceMemberEntity> members = memberRepository.findByWorkspaceIdOrderByCreateAtAsc(workspaceId);
        Map<UUID, UserEntity> users = userRepository
                .findAllById(members.stream().map(WorkspaceMemberEntity::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(UserEntity::getUserId, Function.identity()));

        return members.stream()
                .map(m -> WorkspaceMemberDto.of(m, users.get(m.getUserId())))
                .filter(dto -> dto != null)
                .toList();
    }

    /** 이미 가입한 사용자를 이메일로 초대한다 (FR-WS-005). 미가입자 초대는 2차 범위다. */
    @Transactional
    public WorkspaceMemberDto invite(UUID workspaceId, String email) {
        WorkspaceEntity workspace = requireCollaborativeWorkspace(workspaceId);
        requireAdmin(workspace.getWorkspaceId());

        if (email == null || email.isBlank()) {
            throw new BusinessException(Code.INVALID_REQUEST, "초대할 사용자의 이메일은 필수입니다.");
        }
        UserEntity invitee = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "가입된 사용자를 찾을 수 없습니다."));

        if (memberRepository.existsByWorkspaceIdAndUserId(workspaceId, invitee.getUserId())) {
            throw new BusinessException(Code.INVALID_REQUEST, "이미 워크스페이스에 속한 사용자입니다.");
        }

        WorkspaceMemberEntity member = memberRepository.save(
                new WorkspaceMemberEntity(workspaceId, invitee.getUserId(), WorkspaceRole.MEMBER));
        return WorkspaceMemberDto.of(member, invitee);
    }

    /** 역할 변경 (FR-WS-006). 마지막 관리자는 강등할 수 없다 (FR-WS-008). */
    @Transactional
    public WorkspaceMemberDto changeRole(UUID workspaceId, UUID targetUserId, WorkspaceRole newRole) {
        requireCollaborativeWorkspace(workspaceId);
        requireAdmin(workspaceId);

        WorkspaceMemberEntity target = memberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "구성원을 찾을 수 없습니다."));

        if (target.isAdmin() && newRole != WorkspaceRole.ADMIN) {
            requireNotLastAdmin(workspaceId, "마지막 관리자는 강등할 수 없습니다.");
        }

        target.changeRole(newRole);
        memberRepository.save(target);
        return WorkspaceMemberDto.of(target, userRepository.findById(targetUserId).orElse(null));
    }

    /** 관리자가 구성원을 제거한다 (FR-WS-007). */
    @Transactional
    public void removeMember(UUID workspaceId, UUID targetUserId) {
        requireCollaborativeWorkspace(workspaceId);
        requireAdmin(workspaceId);

        WorkspaceMemberEntity target = memberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "구성원을 찾을 수 없습니다."));

        if (target.isAdmin()) {
            requireNotLastAdmin(workspaceId, "마지막 관리자는 제거할 수 없습니다.");
        }
        memberRepository.delete(target);
    }

    /** 구성원이 스스로 나간다 (FR-WS-007). */
    @Transactional
    public void leave(UUID workspaceId) {
        requireCollaborativeWorkspace(workspaceId);
        WorkspaceMemberEntity me = requireMembership(workspaceId);

        if (me.isAdmin()) {
            requireNotLastAdmin(workspaceId, "마지막 관리자는 워크스페이스를 나갈 수 없습니다.");
        }
        memberRepository.delete(me);
    }

    /** 요청자의 멤버십. 없으면 워크스페이스의 존재 자체를 알리지 않는다. */
    public WorkspaceMemberEntity requireMembership(UUID workspaceId) {
        UUID userId = workspaceContext.requireCurrentUserId();
        return memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "워크스페이스를 찾을 수 없습니다."));
    }

    public WorkspaceMemberEntity requireAdmin(UUID workspaceId) {
        WorkspaceMemberEntity member = requireMembership(workspaceId);
        if (!member.isAdmin()) {
            throw new BusinessException(Code.FORBIDDEN, "워크스페이스 관리자만 수행할 수 있습니다.");
        }
        return member;
    }

    /**
     * 개인 워크스페이스는 초대·역할 변경·구성원 제거가 불가능하다 (FR-WS-009).
     * 구성원이 하나뿐이라 이 동작들이 성립하지 않는다.
     */
    private WorkspaceEntity requireCollaborativeWorkspace(UUID workspaceId) {
        requireMembership(workspaceId);
        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "워크스페이스를 찾을 수 없습니다."));

        if (workspace.isPersonal()) {
            throw new BusinessException(Code.INVALID_REQUEST, "개인 워크스페이스에서는 구성원을 관리할 수 없습니다.");
        }
        return workspace;
    }

    /** 관리자 없는 워크스페이스가 생기지 않게 막는다 (FR-WS-008). */
    private void requireNotLastAdmin(UUID workspaceId, String message) {
        if (memberRepository.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.ADMIN) <= 1) {
            throw new BusinessException(Code.INVALID_REQUEST, message);
        }
    }
}
