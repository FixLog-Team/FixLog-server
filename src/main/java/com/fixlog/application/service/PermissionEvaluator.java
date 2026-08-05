package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.GroupMemberRepository;
import com.fixlog.application.repository.GroupRepository;
import com.fixlog.application.repository.PermissionRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.AuditAction;
import com.fixlog.domain.model.AuditResult;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.domain.model.GroupEntity;
import com.fixlog.domain.model.GroupMemberEntity;
import com.fixlog.domain.model.PermissionAction;
import com.fixlog.domain.model.PermissionEntity;
import com.fixlog.domain.model.PermissionLevel;
import com.fixlog.domain.model.PrincipalType;
import com.fixlog.domain.model.ResourceType;
import com.fixlog.domain.model.WorkspaceMemberEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 접근 판정의 단일 지점 (FR-PRM-003).
 *
 * <p>리포지토리 쿼리에 권한 조건을 넣지 않는 이유가 여기 있다. 조건이 쿼리마다 흩어지면
 * 새 쿼리에서 한 번만 빠뜨려도 권한 우회가 되기 때문이다. 판정은 전부 이 클래스를 지난다.
 *
 * <p>판정 순서 (FR-PRM-004):
 * <ol>
 *   <li>워크스페이스 구성원인가 → 아니면 {@code NOT_FOUND}</li>
 *   <li>워크스페이스 관리자인가 → 전 권한 허용 (FR-PRM-010)</li>
 *   <li>대상에 직접 부여된 권한</li>
 *   <li>조상 폴더에서 상속된 권한 (가까운 조상이 먼 조상을 이긴다)</li>
 *   <li>해당 없음 → {@code FORBIDDEN}</li>
 * </ol>
 *
 * <p>보안 정책(2단계)은 워크스페이스 정책이 들어오는 시점에 이 앞에 붙는다.
 */
@Component
public class PermissionEvaluator {

    /** 빈 IN 절을 만들지 않기 위한 자리표시자. 실제 어떤 값과도 일치하지 않는다. */
    private static final UUID NO_GROUP = new UUID(0L, 0L);
    private static final String NO_ANCESTOR = "";

    private final PermissionRepository permissionRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final WorkspaceContext workspaceContext;
    private final AuditService auditService;

    public PermissionEvaluator(PermissionRepository permissionRepository,
                               WorkspaceMemberRepository workspaceMemberRepository,
                               GroupMemberRepository groupMemberRepository,
                               GroupRepository groupRepository,
                               FolderRepository folderRepository,
                               DocumentRepository documentRepository,
                               WorkspaceContext workspaceContext,
                               AuditService auditService) {
        this.permissionRepository = permissionRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupRepository = groupRepository;
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.workspaceContext = workspaceContext;
        this.auditService = auditService;
    }

    /**
     * 판정 결과. {@code canDownload}가 레벨과 따로 있는 이유는
     * "열람은 되지만 반출은 금지"를 표현해야 하기 때문이다 (FR-PRM-002).
     */
    public record Decision(PermissionLevel level, boolean canDownload, boolean workspaceAdmin) {

        public boolean allows(PermissionAction action) {
            return level.allows(action);
        }
    }

    /**
     * 행위를 허용하지 않으면 예외를 던진다. 호출부는 성공 경로만 신경 쓰면 된다.
     *
     * <p>허용과 거부를 모두 감사 로그에 남긴다. 판정이 전부 이 지점을 지나므로 기록 지점도
     * 여기 하나면 된다 — 서비스마다 흩어 두면 새 경로에서 빠뜨리게 된다 (FR-AUD-001).
     */
    @Transactional(readOnly = true)
    public Decision require(ResourceType resourceType, String resourceId, PermissionAction action) {
        return audited(resourceType, resourceId, auditActionOf(action), () -> {
            Decision decision = evaluate(resourceType, resourceId);
            if (!decision.allows(action)) {
                throw new BusinessException(Code.FORBIDDEN, "이 작업을 수행할 권한이 없습니다.");
            }
            return decision;
        });
    }

    /** 다운로드는 레벨과 분리된 플래그로 판정한다. */
    @Transactional(readOnly = true)
    public Decision requireDownload(ResourceType resourceType, String resourceId) {
        return audited(resourceType, resourceId, AuditAction.DOWNLOAD, () -> {
            Decision decision = evaluate(resourceType, resourceId);
            if (!decision.allows(PermissionAction.VIEW)) {
                throw new BusinessException(Code.FORBIDDEN, "이 작업을 수행할 권한이 없습니다.");
            }
            if (!decision.canDownload()) {
                throw new BusinessException(Code.FORBIDDEN, "이 문서를 다운로드할 권한이 없습니다.");
            }
            return decision;
        });
    }

    /** 판정을 감싸 허용·거부를 남긴다. 거부는 본 트랜잭션이 롤백되므로 별도 트랜잭션에 쓴다. */
    private Decision audited(ResourceType resourceType, String resourceId,
                             AuditAction auditAction, java.util.function.Supplier<Decision> judgement) {
        UUID actorId = workspaceContext.requireCurrentUserId();
        try {
            Decision decision = judgement.get();
            auditService.record(workspaceIdQuietly(resourceType, resourceId), actorId, auditAction,
                    resourceType, resourceId, AuditResult.ALLOWED, decision.workspaceAdmin());
            return decision;
        } catch (BusinessException e) {
            auditService.record(workspaceIdQuietly(resourceType, resourceId), actorId, auditAction,
                    resourceType, resourceId, AuditResult.DENIED, false);
            throw e;
        }
    }

    /** 리소스가 없으면 남길 워크스페이스도 없다. 기록을 위해 예외를 새로 던지지는 않는다. */
    private UUID workspaceIdQuietly(ResourceType resourceType, String resourceId) {
        try {
            return loadTarget(resourceType, resourceId).workspaceId();
        } catch (BusinessException e) {
            return null;
        }
    }

    private AuditAction auditActionOf(PermissionAction action) {
        return switch (action) {
            case VIEW -> AuditAction.VIEW;
            case EDIT -> AuditAction.EDIT;
            case DELETE -> AuditAction.DELETE;
            case SHARE -> AuditAction.SHARE;
        };
    }

    @Transactional(readOnly = true)
    public Decision evaluate(ResourceType resourceType, String resourceId) {
        UUID userId = workspaceContext.requireCurrentUserId();
        Target target = loadTarget(resourceType, resourceId);

        // 1. 구성원이 아니면 리소스의 존재 자체를 알리지 않는다 (FR-PRM-008)
        WorkspaceMemberEntity membership = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(target.workspaceId(), userId)
                .orElseThrow(() -> notFound(resourceType));

        // 2. 관리자는 워크스페이스 안의 모든 리소스에 접근한다 (FR-PRM-010)
        if (membership.isAdmin()) {
            return adminDecision();
        }

        List<UUID> groupIds = groupIdsOf(userId, target.workspaceId());
        List<PermissionEntity> candidates = permissionRepository.findCandidates(
                target.workspaceId(),
                resourceType,
                resourceId,
                target.ancestorFolderIds().isEmpty() ? List.of(NO_ANCESTOR) : target.ancestorFolderIds(),
                userId,
                groupIds.isEmpty() ? List.of(NO_GROUP) : groupIds);

        return resolve(candidates, resourceType, resourceId, target.ancestorFolderIds())
                .orElseThrow(() -> new BusinessException(Code.FORBIDDEN, "이 리소스에 접근할 권한이 없습니다."));
    }

    /**
     * 워크스페이스 하나에 대한 판정 재료를 한 번에 적재한다.
     *
     * <p>목록 조회는 대상이 여러 개다. 대상마다 판정 쿼리를 돌리면 N+1이 되고, 반대로 판정을
     * 건너뛰면 같은 워크스페이스의 모든 문서가 목록에 노출된다. 재료만 미리 모으고
     * <b>판정 규칙 자체는 단건과 같은 {@link #resolve} 하나를 쓴다.</b>
     */
    @Transactional(readOnly = true)
    public Scope scopeFor(UUID workspaceId) {
        return buildScope(workspaceId, true);
    }

    /**
     * 관리자 특권을 무시하고 <b>부여된 권한 레코드만</b>으로 판정하는 범위.
     * "나와 공유됨"처럼 실제로 공유받은 것을 묻는 자리에 쓴다. 관리자에게도 공유는 공유다.
     */
    @Transactional(readOnly = true)
    public Scope explicitScopeFor(UUID workspaceId) {
        return buildScope(workspaceId, false);
    }

    private Scope buildScope(UUID workspaceId, boolean honorAdmin) {
        UUID userId = workspaceContext.requireCurrentUserId();
        WorkspaceMemberEntity membership = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "워크스페이스를 찾을 수 없습니다."));

        if (honorAdmin && membership.isAdmin()) {
            return new Scope(true, List.of(), Map.of());
        }

        List<UUID> groupIds = groupIdsOf(userId, workspaceId);
        List<PermissionEntity> permissions = permissionRepository.findForPrincipals(
                workspaceId, userId, groupIds.isEmpty() ? List.of(NO_GROUP) : groupIds);

        Map<String, String> folderPaths = folderRepository
                .findByWorkspaceIdAndUsable(workspaceId, Integer.valueOf(1)).stream()
                .collect(Collectors.toMap(FolderEntity::getFolderId, FolderEntity::getPath));

        return new Scope(false, permissions, folderPaths);
    }

    /** 한 워크스페이스 안에서 여러 대상을 판정하기 위한 재료 묶음. */
    public final class Scope {

        private final boolean workspaceAdmin;
        private final List<PermissionEntity> permissions;
        private final Map<String, String> folderPaths;

        private Scope(boolean workspaceAdmin,
                      List<PermissionEntity> permissions,
                      Map<String, String> folderPaths) {
            this.workspaceAdmin = workspaceAdmin;
            this.permissions = permissions;
            this.folderPaths = folderPaths;
        }

        public Optional<Decision> decisionForDocument(String documentId, String folderId) {
            if (workspaceAdmin) {
                return Optional.of(adminDecision());
            }
            return resolve(permissions, ResourceType.DOCUMENT, documentId, ancestorsOf(folderId));
        }

        public Optional<Decision> decisionForFolder(String folderId) {
            if (workspaceAdmin) {
                return Optional.of(adminDecision());
            }
            List<String> segments = segmentsOf(folderPaths.get(folderId));
            // 자기 자신은 직접 권한으로 따로 보므로 조상에서 뺀다
            return resolve(permissions, ResourceType.FOLDER, folderId,
                    segments.subList(0, Math.max(0, segments.size() - 1)));
        }

        public boolean canViewDocument(String documentId, String folderId) {
            return decisionForDocument(documentId, folderId)
                    .filter(d -> d.allows(PermissionAction.VIEW)).isPresent();
        }

        public boolean canViewFolder(String folderId) {
            return decisionForFolder(folderId)
                    .filter(d -> d.allows(PermissionAction.VIEW)).isPresent();
        }

        /** 루트 문서는 상속받을 조상이 없다. */
        private List<String> ancestorsOf(String folderId) {
            return folderId == null ? List.of() : segmentsOf(folderPaths.get(folderId));
        }

        private List<String> segmentsOf(String path) {
            if (path == null) {
                return List.of();
            }
            return java.util.Arrays.stream(path.split("/")).filter(s -> !s.isBlank()).toList();
        }
    }

    private Decision adminDecision() {
        return new Decision(PermissionLevel.OWNER, true, true);
    }

    /**
     * 가까운 대상이 먼 조상을 이기고, 같은 대상 안에서는 USER가 GROUP을 이긴다
     * (FR-PRM-005, FR-PRM-006).
     */
    private Optional<Decision> resolve(List<PermissionEntity> candidates,
                                       ResourceType resourceType,
                                       String resourceId,
                                       List<String> ancestorFolderIds) {
        // 대상 자신 → 가장 가까운 조상 → ... → 루트 순서
        List<String> lookupOrder = new ArrayList<>();
        lookupOrder.add(key(resourceType, resourceId));
        List<String> nearestFirst = new ArrayList<>(ancestorFolderIds);
        Collections.reverse(nearestFirst);
        nearestFirst.forEach(folderId -> lookupOrder.add(key(ResourceType.FOLDER, folderId)));

        var byTarget = candidates.stream()
                .collect(Collectors.groupingBy(p -> key(p.getResourceType(), p.getResourceId())));

        for (String target : lookupOrder) {
            List<PermissionEntity> atTarget = byTarget.get(target);
            if (atTarget == null || atTarget.isEmpty()) {
                continue;
            }
            Optional<PermissionEntity> user = strongest(atTarget, PrincipalType.USER);
            if (user.isPresent()) {
                return user.map(this::toDecision);
            }
            Optional<PermissionEntity> group = strongest(atTarget, PrincipalType.GROUP);
            if (group.isPresent()) {
                return group.map(this::toDecision);
            }
        }
        return Optional.empty();
    }

    /** 같은 대상에 같은 종류의 주체가 여럿이면(그룹 여러 개) 가장 강한 것을 따른다. */
    private Optional<PermissionEntity> strongest(List<PermissionEntity> permissions, PrincipalType type) {
        return permissions.stream()
                .filter(p -> p.getPrincipalType() == type)
                .max(Comparator.comparing(p -> p.getPermissionLevel().ordinal()));
    }

    private Decision toDecision(PermissionEntity permission) {
        return new Decision(permission.getPermissionLevel(), permission.isCanDownload(), false);
    }

    private String key(ResourceType type, String id) {
        return type.name() + ":" + id;
    }

    /** 대상이 속한 워크스페이스. 권한 레코드를 만들 때 대상과 같은 워크스페이스에 달기 위한 것이다. */
    @Transactional(readOnly = true)
    public UUID workspaceIdOf(ResourceType resourceType, String resourceId) {
        return loadTarget(resourceType, resourceId).workspaceId();
    }

    /** 대상이 속한 워크스페이스와, 상속 판정에 쓸 조상 폴더 ID들(루트→가까운 순). */
    private record Target(UUID workspaceId, List<String> ancestorFolderIds) {
    }

    private Target loadTarget(ResourceType resourceType, String resourceId) {
        return switch (resourceType) {
            case FOLDER -> {
                FolderEntity folder = folderRepository.findById(resourceId)
                        .filter(f -> Integer.valueOf(1).equals(f.getUsable()))
                        .orElseThrow(() -> notFound(resourceType));
                // 자기 자신은 "직접 권한"으로 따로 보므로 조상에서 제외한다
                List<String> segments = folder.pathSegments();
                yield new Target(folder.getWorkspaceId(),
                        segments.subList(0, Math.max(0, segments.size() - 1)));
            }
            case DOCUMENT -> {
                DocumentEntity document = documentRepository.findById(resourceId)
                        .filter(d -> Integer.valueOf(1).equals(d.getUsable()))
                        .orElseThrow(() -> notFound(resourceType));
                yield new Target(document.getWorkspaceId(), ancestorsOfFolder(document.getFolderId()));
            }
        };
    }

    /** 루트 문서는 상속받을 조상이 없다. */
    private List<String> ancestorsOfFolder(String folderId) {
        if (folderId == null) {
            return List.of();
        }
        return folderRepository.findById(folderId)
                .map(FolderEntity::pathSegments)
                .orElse(List.of());
    }

    private List<UUID> groupIdsOf(UUID userId, UUID workspaceId) {
        Set<UUID> memberOf = groupMemberRepository.findByUserId(userId).stream()
                .map(GroupMemberEntity::getGroupId)
                .collect(Collectors.toSet());
        if (memberOf.isEmpty()) {
            return List.of();
        }
        // 그룹은 워크스페이스를 넘지 않는다. 다른 워크스페이스의 그룹이 판정에 섞이지 않게 거른다.
        return groupRepository.findAllById(memberOf).stream()
                .filter(group -> group.getWorkspaceId().equals(workspaceId))
                .map(GroupEntity::getGroupId)
                .toList();
    }

    private BusinessException notFound(ResourceType resourceType) {
        String label = resourceType == ResourceType.FOLDER ? "폴더" : "문서";
        return new BusinessException(Code.NOT_FOUND, label + "를 찾을 수 없습니다.");
    }
}
