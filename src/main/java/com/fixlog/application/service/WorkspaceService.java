package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.PermissionRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.repository.WorkspaceInvitationRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.application.repository.WorkspaceRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.AuditAction;
import com.fixlog.domain.model.PrincipalType;
import com.fixlog.domain.model.PermissionType;
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
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final PermissionRepository permissionRepository;
    private final WorkspaceInvitationRepository invitationRepository;
    private final AuditService auditService;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
                            WorkspaceMemberRepository memberRepository,
                            UserRepository userRepository,
                            WorkspaceContext workspaceContext,
                            FolderRepository folderRepository,
                            DocumentRepository documentRepository,
                            PermissionRepository permissionRepository,
                            WorkspaceInvitationRepository invitationRepository,
                            AuditService auditService) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.workspaceContext = workspaceContext;
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.permissionRepository = permissionRepository;
        this.invitationRepository = invitationRepository;
        this.auditService = auditService;
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
                            workspace.getWorkspaceId(), user.getUserId(), WorkspaceRole.OWNER));
                    return workspace;
                });
    }

    /** 누구나 워크스페이스를 만들 수 있고, 만든 사람이 Owner가 된다 (FR-WS-001). */
    @Transactional
    public WorkspaceEntity create(String workspaceName) {
        if (workspaceName == null || workspaceName.isBlank()) {
            throw new BusinessException(Code.INVALID_REQUEST, "워크스페이스 이름은 필수입니다.");
        }
        UUID userId = workspaceContext.requireCurrentUserId();

        WorkspaceEntity workspace = workspaceRepository.save(WorkspaceEntity.shared(workspaceName.trim()));
        memberRepository.save(new WorkspaceMemberEntity(
                workspace.getWorkspaceId(), userId, WorkspaceRole.OWNER));
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

        auditService.recordChange(workspaceId, workspaceContext.requireCurrentUserId(),
                AuditAction.MEMBER_INVITE, null, null,
                PrincipalType.USER, invitee.getUserId(), "MEMBER로 합류");
        return WorkspaceMemberDto.of(member, invitee);
    }

    /**
     * 역할 변경 (FR-WS-006).
     * OWNER 역할 부여/해제는 현재 OWNER만 할 수 있다. 마지막 OWNER는 강등할 수 없다.
     */
    @Transactional
    public WorkspaceMemberDto changeRole(UUID workspaceId, UUID targetUserId, WorkspaceRole newRole) {
        requireCollaborativeWorkspace(workspaceId);

        WorkspaceMemberEntity target = memberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "구성원을 찾을 수 없습니다."));

        boolean ownerInvolved = newRole == WorkspaceRole.OWNER || target.isOwner();
        if (ownerInvolved) {
            requireOwner(workspaceId);
        } else {
            requireAdmin(workspaceId);
        }

        if (target.isOwner() && newRole != WorkspaceRole.OWNER) {
            requireNotLastOwner(workspaceId, "마지막 Owner는 강등할 수 없습니다.");
        }
        if (target.isAdminOrOwner() && newRole == WorkspaceRole.MEMBER) {
            requireNotLastAdmin(workspaceId, "마지막 관리자는 강등할 수 없습니다.");
        }

        WorkspaceRole previousRole = target.getRole();
        target.changeRole(newRole);
        memberRepository.save(target);

        auditService.recordChange(workspaceId, workspaceContext.requireCurrentUserId(),
                AuditAction.ROLE_CHANGE, null, null,
                PrincipalType.USER, targetUserId, previousRole + " → " + newRole);
        return WorkspaceMemberDto.of(target, userRepository.findById(targetUserId).orElse(null));
    }

    /** Admin/Owner가 구성원을 제거한다 (FR-WS-007). */
    @Transactional
    public void removeMember(UUID workspaceId, UUID targetUserId) {
        requireCollaborativeWorkspace(workspaceId);
        requireAdmin(workspaceId);

        WorkspaceMemberEntity target = memberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "구성원을 찾을 수 없습니다."));

        if (target.isOwner()) {
            requireNotLastOwner(workspaceId, "마지막 Owner는 제거할 수 없습니다.");
        }
        if (target.isAdminOrOwner()) {
            requireNotLastAdmin(workspaceId, "마지막 관리자는 제거할 수 없습니다.");
        }
        memberRepository.delete(target);

        auditService.recordChange(workspaceId, workspaceContext.requireCurrentUserId(),
                AuditAction.MEMBER_REMOVE, null, null,
                PrincipalType.USER, targetUserId, "내보내짐 (" + target.getRole() + ")");
    }

    /** 구성원이 스스로 나간다 (FR-WS-007). */
    @Transactional
    public void leave(UUID workspaceId) {
        requireCollaborativeWorkspace(workspaceId);
        WorkspaceMemberEntity me = requireMembership(workspaceId);

        if (me.isOwner()) {
            requireNotLastOwner(workspaceId, "마지막 Owner는 워크스페이스를 나갈 수 없습니다.");
        }
        if (me.isAdminOrOwner()) {
            requireNotLastAdmin(workspaceId, "마지막 관리자는 워크스페이스를 나갈 수 없습니다.");
        }
        memberRepository.delete(me);

        auditService.recordChange(workspaceId, me.getUserId(),
                AuditAction.MEMBER_REMOVE, null, null,
                PrincipalType.USER, me.getUserId(), "스스로 나감 (" + me.getRole() + ")");
    }

    /** 워크스페이스 단건 조회. 구성원만 가능하며 비구성원에게는 존재를 알리지 않는다. */
    @Transactional(readOnly = true)
    public WorkspaceDto get(UUID workspaceId) {
        WorkspaceMemberEntity me = requireMembership(workspaceId);
        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "워크스페이스를 찾을 수 없습니다."));
        return WorkspaceDto.of(workspace, me.getRole());
    }

    /** 워크스페이스 이름 변경 (Admin/Owner). */
    @Transactional
    public WorkspaceDto rename(UUID workspaceId, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new BusinessException(Code.INVALID_REQUEST, "워크스페이스 이름은 필수입니다.");
        }
        WorkspaceMemberEntity me = requireAdmin(workspaceId);
        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "워크스페이스를 찾을 수 없습니다."));
        workspace.rename(newName.trim());
        workspaceRepository.save(workspace);
        return WorkspaceDto.of(workspace, me.getRole());
    }

    /**
     * 기본 접근 정책 변경. 상속 체인이 루트까지 올라갔을 때 적용되는 값이다.
     *
     * <p>DENY로 두면 명시적으로 부여한 권한만 열리고, ALLOW로 두면 구성원 전원이
     * 별도 설정 없이 접근한다. 워크스페이스 전체의 노출 범위를 한 번에 바꾸는 설정이라
     * 관리자만 변경할 수 있다.
     */
    @Transactional
    public WorkspaceDto changeBaseAccess(UUID workspaceId, PermissionType baseAccess) {
        WorkspaceMemberEntity me = requireAdmin(workspaceId);
        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "워크스페이스를 찾을 수 없습니다."));
        PermissionType previous = workspace.getBaseAccess();
        workspace.changeBaseAccess(baseAccess);
        workspaceRepository.save(workspace);

        auditService.recordChange(workspaceId, me.getUserId(),
                AuditAction.ACCESS_POLICY_CHANGE, null, null, null, null,
                "워크스페이스 기본 접근 " + previous + " → " + baseAccess);
        return WorkspaceDto.of(workspace, me.getRole());
    }

    /**
     * 워크스페이스 삭제 (Owner 전용, 개인 워크스페이스 불가).
     * 내부 폴더·문서 soft-delete, 구성원·초대·권한 hard-delete.
     */
    @Transactional
    public void delete(UUID workspaceId) {
        requireOwner(workspaceId);
        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "워크스페이스를 찾을 수 없습니다."));
        if (workspace.isPersonal()) {
            throw new BusinessException(Code.INVALID_REQUEST, "개인 워크스페이스는 삭제할 수 없습니다.");
        }

        folderRepository.softDeleteByWorkspaceId(workspaceId);
        documentRepository.softDeleteByWorkspaceId(workspaceId);
        permissionRepository.deleteByWorkspaceId(workspaceId);
        invitationRepository.deleteByWorkspaceId(workspaceId);
        memberRepository.deleteByWorkspaceId(workspaceId);
        workspaceRepository.delete(workspace);
    }

    /** 요청자의 멤버십. 없으면 워크스페이스의 존재 자체를 알리지 않는다. */
    public WorkspaceMemberEntity requireMembership(UUID workspaceId) {
        UUID userId = workspaceContext.requireCurrentUserId();
        return memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "워크스페이스를 찾을 수 없습니다."));
    }

    public WorkspaceMemberEntity requireAdmin(UUID workspaceId) {
        WorkspaceMemberEntity member = requireMembership(workspaceId);
        if (!member.isAdminOrOwner()) {
            throw new BusinessException(Code.FORBIDDEN, "워크스페이스 관리자만 수행할 수 있습니다.");
        }
        return member;
    }

    public WorkspaceMemberEntity requireOwner(UUID workspaceId) {
        WorkspaceMemberEntity member = requireMembership(workspaceId);
        if (!member.isOwner()) {
            throw new BusinessException(Code.FORBIDDEN, "워크스페이스 Owner만 수행할 수 있습니다.");
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

    private void requireNotLastAdmin(UUID workspaceId, String message) {
        long adminCount = memberRepository.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.ADMIN)
                + memberRepository.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER);
        if (adminCount <= 1) {
            throw new BusinessException(Code.INVALID_REQUEST, message);
        }
    }

    private void requireNotLastOwner(UUID workspaceId, String message) {
        if (memberRepository.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER) <= 1) {
            throw new BusinessException(Code.INVALID_REQUEST, message);
        }
    }
}
