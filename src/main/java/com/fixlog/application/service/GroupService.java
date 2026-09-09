package com.fixlog.application.service;

import com.fixlog.application.repository.GroupMemberRepository;
import com.fixlog.application.repository.GroupRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.AuditAction;
import com.fixlog.domain.model.PrincipalType;
import com.fixlog.domain.model.WorkspaceMemberEntity;
import com.fixlog.domain.model.GroupEntity;
import com.fixlog.domain.model.GroupMemberEntity;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.presentation.dto.response.GroupMemberDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 워크스페이스 내 그룹 관리 (FR-WS-010, FR-WS-011).
 *
 * <p>모든 조회는 워크스페이스를 함께 건다. 그룹 ID만으로 접근하면 다른 워크스페이스의 그룹을
 * 건드릴 수 있기 때문이다.
 */
@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final WorkspaceService workspaceService;
    private final AuditService auditService;

    public GroupService(GroupRepository groupRepository,
                        GroupMemberRepository groupMemberRepository,
                        WorkspaceMemberRepository workspaceMemberRepository,
                        UserRepository userRepository,
                        WorkspaceService workspaceService,
                        AuditService auditService) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.workspaceService = workspaceService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<GroupEntity> list(UUID workspaceId) {
        workspaceService.requireMembership(workspaceId);
        return groupRepository.findByWorkspaceIdOrderByCreateAtAsc(workspaceId);
    }

    @Transactional
    public GroupEntity create(UUID workspaceId, String groupName) {
        workspaceService.requireAdmin(workspaceId);
        String name = requireName(groupName);

        if (groupRepository.existsByWorkspaceIdAndGroupName(workspaceId, name)) {
            throw new BusinessException(Code.INVALID_REQUEST, "같은 이름의 그룹이 이미 있습니다.");
        }
        return groupRepository.save(new GroupEntity(workspaceId, name));
    }

    @Transactional
    public GroupEntity rename(UUID workspaceId, UUID groupId, String groupName) {
        workspaceService.requireAdmin(workspaceId);
        GroupEntity group = requireGroup(workspaceId, groupId);
        String name = requireName(groupName);

        if (!group.getGroupName().equals(name)
                && groupRepository.existsByWorkspaceIdAndGroupName(workspaceId, name)) {
            throw new BusinessException(Code.INVALID_REQUEST, "같은 이름의 그룹이 이미 있습니다.");
        }
        group.rename(name);
        return groupRepository.save(group);
    }

    @Transactional
    public void delete(UUID workspaceId, UUID groupId) {
        workspaceService.requireAdmin(workspaceId);
        GroupEntity group = requireGroup(workspaceId, groupId);

        groupMemberRepository.deleteByGroupId(group.getGroupId());
        groupRepository.delete(group);
    }

    @Transactional(readOnly = true)
    public List<GroupMemberDto> members(UUID workspaceId, UUID groupId) {
        workspaceService.requireMembership(workspaceId);
        requireGroup(workspaceId, groupId);

        List<GroupMemberEntity> members = groupMemberRepository.findByGroupIdOrderByCreateAtAsc(groupId);
        Map<UUID, UserEntity> users = userRepository
                .findAllById(members.stream().map(GroupMemberEntity::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(UserEntity::getUserId, Function.identity()));

        return members.stream()
                .map(m -> GroupMemberDto.from(users.get(m.getUserId())))
                .filter(dto -> dto != null)
                .toList();
    }

    @Transactional
    public void addMember(UUID workspaceId, UUID groupId, UUID userId) {
        WorkspaceMemberEntity admin = workspaceService.requireAdmin(workspaceId);
        requireGroup(workspaceId, groupId);

        // 그룹은 워크스페이스 경계를 넘지 않는다. 워크스페이스 구성원만 그룹에 들어갈 수 있다.
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new BusinessException(Code.NOT_FOUND, "워크스페이스 구성원이 아닙니다.");
        }
        if (groupMemberRepository.findByGroupIdAndUserId(groupId, userId).isPresent()) {
            throw new BusinessException(Code.INVALID_REQUEST, "이미 그룹에 속한 사용자입니다.");
        }
        groupMemberRepository.save(new GroupMemberEntity(groupId, userId));

        // 그룹에 걸린 권한이 그대로 따라가므로 권한 변경으로 남긴다.
        auditService.recordChange(workspaceId, admin.getUserId(),
                AuditAction.GROUP_MEMBER_CHANGE, null, null,
                PrincipalType.USER, userId, "그룹 합류 (groupId=" + groupId + ")");
    }

    @Transactional
    public void removeMember(UUID workspaceId, UUID groupId, UUID userId) {
        WorkspaceMemberEntity admin = workspaceService.requireAdmin(workspaceId);
        requireGroup(workspaceId, groupId);

        GroupMemberEntity member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "그룹 구성원을 찾을 수 없습니다."));
        groupMemberRepository.delete(member);

        auditService.recordChange(workspaceId, admin.getUserId(),
                AuditAction.GROUP_MEMBER_CHANGE, null, null,
                PrincipalType.USER, userId, "그룹 탈퇴 (groupId=" + groupId + ")");
    }

    private GroupEntity requireGroup(UUID workspaceId, UUID groupId) {
        return groupRepository.findByGroupIdAndWorkspaceId(groupId, workspaceId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "그룹을 찾을 수 없습니다."));
    }

    private String requireName(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            throw new BusinessException(Code.INVALID_REQUEST, "그룹 이름은 필수입니다.");
        }
        return groupName.trim();
    }
}
