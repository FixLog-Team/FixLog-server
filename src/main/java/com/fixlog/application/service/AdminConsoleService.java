package com.fixlog.application.service;

import com.fixlog.application.repository.AuditLogRepository;
import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.GroupRepository;
import com.fixlog.application.repository.PermissionRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.domain.model.AuditAction;
import com.fixlog.domain.model.AuditLogEntity;
import com.fixlog.domain.model.AuditResult;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.domain.model.PermissionEntity;
import com.fixlog.domain.model.ResourceType;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.presentation.dto.response.AdminPermissionDto;
import com.fixlog.presentation.dto.response.AuditLogDto;
import com.fixlog.presentation.dto.response.WorkspaceStatsDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final WorkspaceService workspaceService;

    public AdminConsoleService(PermissionRepository permissionRepository,
                               AuditLogRepository auditLogRepository,
                               DocumentRepository documentRepository,
                               FolderRepository folderRepository,
                               UserRepository userRepository,
                               GroupRepository groupRepository,
                               WorkspaceService workspaceService) {
        this.permissionRepository = permissionRepository;
        this.auditLogRepository = auditLogRepository;
        this.documentRepository = documentRepository;
        this.folderRepository = folderRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
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
    public List<AuditLogDto> auditLogs(UUID workspaceId, UUID actorUserId, AuditAction action,
                                       AuditResult result, Instant from, Instant to) {
        workspaceService.requireAdmin(workspaceId);
        List<AuditLogEntity> logs = auditLogRepository.search(workspaceId, actorUserId, action, result, from, to);

        Map<UUID, String> userNames = userNames(logs.stream()
                .map(AuditLogEntity::getActorUserId).distinct().toList());

        return logs.stream()
                .map(entry -> AuditLogDto.of(entry, userNames.get(entry.getActorUserId())))
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
