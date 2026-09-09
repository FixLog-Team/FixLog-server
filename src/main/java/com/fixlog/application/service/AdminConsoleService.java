package com.fixlog.application.service;

import com.fixlog.application.repository.AuditLogRepository;
import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.GroupRepository;
import com.fixlog.application.repository.PermissionRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.AuditAction;
import com.fixlog.domain.model.AuditLogEntity;
import com.fixlog.domain.model.AuditResult;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.domain.model.PermissionEntity;
import com.fixlog.domain.model.ResourceType;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.WorkspaceMemberEntity;
import com.fixlog.presentation.dto.response.AdminPermissionDto;
import com.fixlog.presentation.dto.response.AdminUserDto;
import com.fixlog.presentation.dto.response.AuditLogDto;
import com.fixlog.presentation.dto.response.WorkspaceStatsDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 워크스페이스 관리자 콘솔 (FR-ADM-001~007).
 *
 * <p>모든 조회는 <b>요청자가 관리자인 워크스페이스로 범위가 한정된다.</b> 전역(크로스 워크스페이스)
 * 조회 경로는 만들지 않는다 — 그 자체가 권한 우회 통로가 되기 때문이다 (D2, FR-ADM-007).
 */
@Service
public class AdminConsoleService {

    private final PermissionRepository permissionRepository;
    private final AuditLogRepository auditLogRepository;
    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceService workspaceService;

    public AdminConsoleService(PermissionRepository permissionRepository,
                               AuditLogRepository auditLogRepository,
                               DocumentRepository documentRepository,
                               FolderRepository folderRepository,
                               UserRepository userRepository,
                               GroupRepository groupRepository,
                               WorkspaceMemberRepository memberRepository,
                               WorkspaceService workspaceService) {
        this.permissionRepository = permissionRepository;
        this.auditLogRepository = auditLogRepository;
        this.documentRepository = documentRepository;
        this.folderRepository = folderRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.workspaceService = workspaceService;
    }

    /** 리소스별 권한 현황 (FR-ADM-002). */
    @Transactional(readOnly = true)
    public List<AdminPermissionDto> permissions(UUID workspaceId) {
        workspaceService.requireAdmin(workspaceId);
        return describe(permissionRepository.findByWorkspaceIdOrderByCreateAtDesc(workspaceId));
    }

    /**
     * 공유 현황 (FR-ADM-003).
     *
     * <p>생성자에게 자동으로 붙는 소유 권한은 공유가 아니다. 그것까지 세면 모든 문서가
     * "공유됨"으로 잡혀 목록이 쓸모없어진다.
     */
    @Transactional(readOnly = true)
    public List<AdminPermissionDto> shares(UUID workspaceId) {
        workspaceService.requireAdmin(workspaceId);
        List<PermissionEntity> shared = permissionRepository
                .findByWorkspaceIdOrderByCreateAtDesc(workspaceId).stream()
                .filter(p -> !isCreatorOwnership(p))
                .toList();
        return describe(shared);
    }

    private boolean isCreatorOwnership(PermissionEntity permission) {
        return permission.getGrantedBy() != null
                && permission.getGrantedBy().equals(permission.getPrincipalId());
    }

    /** 감사 로그 필터 조회 (FR-ADM-004, FR-AUD-005). */
    @Transactional(readOnly = true)
    public List<AuditLogDto> auditLogs(UUID workspaceId, UUID actorUserId, UUID targetUserId,
                                       AuditAction action, AuditResult result,
                                       Instant from, Instant to) {
        workspaceService.requireAdmin(workspaceId);
        List<AuditLogEntity> logs = auditLogRepository.search(
                workspaceId, actorUserId, targetUserId, action, result, from, to);

        // 행위자와 대상을 함께 이름으로 바꾼다. 같은 사람이 양쪽에 나올 수 있어 한 번에 조회한다.
        Map<UUID, String> userNames = userNames(logs.stream()
                .flatMap(entry -> Stream.of(entry.getActorUserId(), entry.getTargetPrincipalId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList());

        return logs.stream()
                .map(entry -> AuditLogDto.of(entry,
                        userNames.get(entry.getActorUserId()),
                        userNames.get(entry.getTargetPrincipalId())))
                .toList();
    }

    /** 문서·폴더 현황 (FR-ADM-005). */
    @Transactional(readOnly = true)
    public WorkspaceStatsDto stats(UUID workspaceId) {
        workspaceService.requireAdmin(workspaceId);

        Map<String, Long> byUserId = documentRepository.countDocumentsByUser(workspaceId).stream()
                .collect(Collectors.toMap(
                        DocumentRepository.UserDocumentCount::getUserId,
                        DocumentRepository.UserDocumentCount::getDocumentCount));

        Map<String, Long> byUserName = new HashMap<>();
        byUserId.forEach((userId, count) -> byUserName.put(userNameOf(userId), count));

        return new WorkspaceStatsDto(
                documentRepository.countByWorkspaceIdAndUsable(workspaceId, Integer.valueOf(1)),
                folderRepository.countByWorkspaceIdAndUsable(workspaceId, Integer.valueOf(1)),
                documentRepository.countByWorkspaceIdAndUsable(workspaceId, Integer.valueOf(0)),
                folderRepository.countByWorkspaceIdAndUsable(workspaceId, Integer.valueOf(0)),
                byUserName);
    }

    /** 워크스페이스 구성원 목록 (FR-ADM-009). */
    @Transactional(readOnly = true)
    public List<AdminUserDto> listUsers(UUID workspaceId) {
        workspaceService.requireAdmin(workspaceId);
        List<WorkspaceMemberEntity> members = memberRepository.findByWorkspaceIdOrderByCreateAtAsc(workspaceId);
        Map<UUID, UserEntity> users = userRepository
                .findAllById(members.stream().map(WorkspaceMemberEntity::getUserId).toList()).stream()
                .collect(Collectors.toMap(UserEntity::getUserId, Function.identity()));
        return members.stream()
                .map(m -> AdminUserDto.of(m, users.get(m.getUserId())))
                .filter(dto -> dto != null)
                .toList();
    }

    /** 특정 구성원 상세 조회. */
    @Transactional(readOnly = true)
    public AdminUserDto getUser(UUID workspaceId, UUID targetUserId) {
        workspaceService.requireAdmin(workspaceId);
        WorkspaceMemberEntity member = memberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "구성원을 찾을 수 없습니다."));
        UserEntity user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        return AdminUserDto.of(member, user);
    }

    /** 권한 목록에 사람이 읽을 이름을 붙인다. ID만 나열하면 콘솔에서 쓸 수 없다. */
    private List<AdminPermissionDto> describe(List<PermissionEntity> permissions) {
        Map<UUID, String> principalNames = new HashMap<>();
        permissions.forEach(p -> principalNames.computeIfAbsent(p.getPrincipalId(),
                id -> switch (p.getPrincipalType()) {
                    case USER -> userRepository.findById(id).map(UserEntity::getUserName).orElse(null);
                    case GROUP -> groupRepository.findById(id).map(g -> g.getGroupName()).orElse(null);
                }));

        Map<String, String> resourceNames = new HashMap<>();
        permissions.forEach(p -> resourceNames.computeIfAbsent(
                p.getResourceType().name() + ":" + p.getResourceId(),
                key -> resourceNameOf(p.getResourceType(), p.getResourceId())));

        return permissions.stream()
                .map(p -> AdminPermissionDto.of(p,
                        principalNames.get(p.getPrincipalId()),
                        resourceNames.get(p.getResourceType().name() + ":" + p.getResourceId())))
                .toList();
    }

    private String resourceNameOf(ResourceType type, String id) {
        return switch (type) {
            case DOCUMENT -> documentRepository.findById(id).map(DocumentEntity::getTitle).orElse(null);
            case FOLDER -> folderRepository.findById(id).map(FolderEntity::getFolderName).orElse(null);
        };
    }

    private Map<UUID, String> userNames(List<UUID> userIds) {
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getUserId, UserEntity::getUserName, (a, b) -> a));
    }

    /** 작성자 컬럼은 UUID 문자열이다. 형식이 어긋난 값이 있어도 통계가 죽지 않게 한다. */
    private String userNameOf(String rawUserId) {
        try {
            return userRepository.findById(UUID.fromString(rawUserId))
                    .map(UserEntity::getUserName)
                    .orElse(rawUserId);
        } catch (IllegalArgumentException e) {
            return rawUserId;
        }
    }
}
