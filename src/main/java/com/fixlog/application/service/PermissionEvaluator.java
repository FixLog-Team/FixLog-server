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
import com.fixlog.domain.model.PermissionSource;
import com.fixlog.domain.model.PermissionType;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 접근 판정의 단일 지점 (FR-PRM-003).
 *
 * <p>판정 순서 (FR-PRM-004):
 * <ol>
 *   <li>워크스페이스 구성원인가 → 아니면 {@code NOT_FOUND}</li>
 *   <li>워크스페이스 OWNER/ADMIN인가 → 전 권한 허용</li>
 *   <li>대상에 직접 부여된 권한 (USER &gt; GROUP): DENY면 즉시 차단, ALLOW면 허용</li>
 *   <li>조상 폴더 순회 (가까운→먼, inheritFromParent=false 폴더에서 체인 중단)</li>
 *   <li>Base Access 확인 (가장 가까운 독립 폴더의 baseAccess)</li>
 *   <li>워크스페이스 기본 → ALLOW (기획서: 기본은 All Workspace Users → Allow)</li>
 * </ol>
 */
@Component
public class PermissionEvaluator {

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
    private final com.fixlog.application.repository.SecurityPolicyRepository policyRepository;

    public PermissionEvaluator(PermissionRepository permissionRepository,
                               WorkspaceMemberRepository workspaceMemberRepository,
                               GroupMemberRepository groupMemberRepository,
                               GroupRepository groupRepository,
                               FolderRepository folderRepository,
                               DocumentRepository documentRepository,
                               WorkspaceContext workspaceContext,
                               AuditService auditService,
                               com.fixlog.application.repository.SecurityPolicyRepository policyRepository) {
        this.permissionRepository = permissionRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupRepository = groupRepository;
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.workspaceContext = workspaceContext;
        this.auditService = auditService;
        this.policyRepository = policyRepository;
    }

    /**
     * 판정 결과.
     *
     * <p>{@code allowed}가 false인 채로 반환되는 경우는 없다 — 접근 불가이면 예외가 먼저 던져진다.
     * {@code canDownload}는 ALLOW 상태에서만 의미 있다.
     */
    public record Decision(boolean allowed, boolean canDownload, boolean workspaceAdmin) {
    }

    /**
     * 행위를 허용하지 않으면 예외를 던진다.
     * 허용과 거부를 모두 감사 로그에 남긴다.
     */
    @Transactional(readOnly = true)
    public Decision require(ResourceType resourceType, String resourceId, PermissionAction action) {
        return audited(resourceType, resourceId, auditActionOf(action), () -> {
            requirePolicyAllows(resourceType, resourceId, action);
            return evaluate(resourceType, resourceId);
        });
    }

    @Transactional(readOnly = true)
    public Decision requireDownload(ResourceType resourceType, String resourceId) {
        return audited(resourceType, resourceId, AuditAction.DOWNLOAD, () -> {
            if (!policyOf(resourceType, resourceId).isAllowDownload()) {
                throw new BusinessException(Code.FORBIDDEN, "워크스페이스 정책에서 다운로드가 금지되어 있습니다.");
            }
            Decision decision = evaluate(resourceType, resourceId);
            if (!decision.canDownload()) {
                throw new BusinessException(Code.FORBIDDEN, "이 문서를 다운로드할 권한이 없습니다.");
            }
            return decision;
        });
    }

    private void requirePolicyAllows(ResourceType resourceType, String resourceId, PermissionAction action) {
        if (action == PermissionAction.SHARE && !policyOf(resourceType, resourceId).isAllowSharing()) {
            throw new BusinessException(Code.FORBIDDEN, "워크스페이스 정책에서 공유가 금지되어 있습니다.");
        }
    }

    private com.fixlog.domain.model.SecurityPolicyEntity policyOf(ResourceType resourceType, String resourceId) {
        UUID workspaceId = workspaceIdQuietly(resourceType, resourceId);
        if (workspaceId == null) {
            return new com.fixlog.domain.model.SecurityPolicyEntity(new UUID(0L, 0L));
        }
        return policyRepository.findById(workspaceId)
                .orElseGet(() -> new com.fixlog.domain.model.SecurityPolicyEntity(workspaceId));
    }

    private Decision audited(ResourceType resourceType, String resourceId,
                             AuditAction auditAction, Supplier<Decision> judgement) {
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
        return evaluateFor(userId, resourceType, resourceId);
    }

    /**
     * 관리자가 다른 사용자의 권한을 조회할 때 사용한다.
     * SecurityContext를 우회해 {@code targetUserId}를 직접 받는다.
     */
    @Transactional(readOnly = true)
    public Decision evaluateFor(UUID targetUserId, ResourceType resourceType, String resourceId) {
        Target target = loadTarget(resourceType, resourceId);

        WorkspaceMemberEntity membership = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(target.workspaceId(), targetUserId)
                .orElseThrow(() -> notFound(resourceType));

        if (membership.isAdminOrOwner()) {
            return adminDecision();
        }

        List<UUID> groupIds = groupIdsOf(targetUserId, target.workspaceId());
        List<PermissionEntity> candidates = permissionRepository.findCandidates(
                target.workspaceId(),
                resourceType,
                resourceId,
                target.ancestorFolderIds().isEmpty() ? List.of(NO_ANCESTOR) : target.ancestorFolderIds(),
                targetUserId,
                groupIds.isEmpty() ? List.of(NO_GROUP) : groupIds);

        List<FolderEntity> ancestorFolders = loadAncestorFolders(target.ancestorFolderIds());

        return resolveWithDeny(candidates, resourceType, resourceId,
                target.ancestorFolderIds(), ancestorFolders);
    }

    /**
     * 관리자가 다른 사용자의 유효 권한을 출처와 함께 조회할 때 사용한다.
     */
    @Transactional(readOnly = true)
    public DecisionWithSource evaluateForWithSource(UUID targetUserId, ResourceType resourceType, String resourceId) {
        Target target = loadTarget(resourceType, resourceId);

        WorkspaceMemberEntity membership = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(target.workspaceId(), targetUserId)
                .orElseThrow(() -> notFound(resourceType));

        if (membership.isAdminOrOwner()) {
            return new DecisionWithSource(adminDecision(), PermissionSource.DIRECT, "관리자 특권");
        }

        List<UUID> groupIds = groupIdsOf(targetUserId, target.workspaceId());
        List<PermissionEntity> candidates = permissionRepository.findCandidates(
                target.workspaceId(),
                resourceType,
                resourceId,
                target.ancestorFolderIds().isEmpty() ? List.of(NO_ANCESTOR) : target.ancestorFolderIds(),
                targetUserId,
                groupIds.isEmpty() ? List.of(NO_GROUP) : groupIds);

        List<FolderEntity> ancestorFolders = loadAncestorFolders(target.ancestorFolderIds());

        return resolveWithSource(candidates, resourceType, resourceId,
                target.ancestorFolderIds(), ancestorFolders);
    }

    public record DecisionWithSource(Decision decision, PermissionSource source, String sourceDetail) {
    }

    @Transactional(readOnly = true)
    public Scope scopeFor(UUID workspaceId) {
        return buildScope(workspaceId, true);
    }

    @Transactional(readOnly = true)
    public Scope explicitScopeFor(UUID workspaceId) {
        return buildScope(workspaceId, false);
    }

    private Scope buildScope(UUID workspaceId, boolean honorAdmin) {
        UUID userId = workspaceContext.requireCurrentUserId();
        WorkspaceMemberEntity membership = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "워크스페이스를 찾을 수 없습니다."));

        if (honorAdmin && membership.isAdminOrOwner()) {
            return new Scope(true, List.of(), Map.of());
        }

        List<UUID> groupIds = groupIdsOf(userId, workspaceId);
        List<PermissionEntity> permissions = permissionRepository.findForPrincipals(
                workspaceId, userId, groupIds.isEmpty() ? List.of(NO_GROUP) : groupIds);

        Map<String, FolderAccessInfo> folderInfo = folderRepository
                .findByWorkspaceIdAndUsable(workspaceId, Integer.valueOf(1)).stream()
                .collect(Collectors.toMap(
                        FolderEntity::getFolderId,
                        f -> new FolderAccessInfo(f.getPath(), f.isInheritFromParent(), f.getBaseAccess())));

        return new Scope(false, permissions, folderInfo);
    }

    /** 폴더의 경로·상속·기본접근 정보를 경량으로 담는다. */
    public record FolderAccessInfo(String path, boolean inheritFromParent, PermissionType baseAccess) {
    }

    public final class Scope {

        private final boolean workspaceAdmin;
        private final List<PermissionEntity> permissions;
        private final Map<String, FolderAccessInfo> folderInfo;

        private Scope(boolean workspaceAdmin,
                      List<PermissionEntity> permissions,
                      Map<String, FolderAccessInfo> folderInfo) {
            this.workspaceAdmin = workspaceAdmin;
            this.permissions = permissions;
            this.folderInfo = folderInfo;
        }

        public Optional<Decision> decisionForDocument(String documentId, String folderId) {
            if (workspaceAdmin) {
                return Optional.of(adminDecision());
            }
            return optionalResolve(permissions, ResourceType.DOCUMENT, documentId, ancestorsOf(folderId));
        }

        public Optional<Decision> decisionForFolder(String folderId) {
            if (workspaceAdmin) {
                return Optional.of(adminDecision());
            }
            List<String> segments = segmentsOf(folderInfo.containsKey(folderId)
                    ? folderInfo.get(folderId).path() : null);
            return optionalResolve(permissions, ResourceType.FOLDER, folderId,
                    segments.subList(0, Math.max(0, segments.size() - 1)));
        }

        public boolean canViewDocument(String documentId, String folderId) {
            return decisionForDocument(documentId, folderId)
                    .filter(Decision::allowed).isPresent();
        }

        public boolean canViewFolder(String folderId) {
            return decisionForFolder(folderId)
                    .filter(Decision::allowed).isPresent();
        }

        private List<String> ancestorsOf(String folderId) {
            return folderId == null ? List.of()
                    : segmentsOf(folderInfo.containsKey(folderId) ? folderInfo.get(folderId).path() : null);
        }

        private List<String> segmentsOf(String path) {
            if (path == null) return List.of();
            return java.util.Arrays.stream(path.split("/")).filter(s -> !s.isBlank()).toList();
        }

        /** DENY 시 예외 대신 빈 Optional을 반환한다. Scope 목록 조회에서는 예외 없이 필터링한다. */
        private Optional<Decision> optionalResolve(List<PermissionEntity> candidates,
                                                   ResourceType resourceType,
                                                   String resourceId,
                                                   List<String> ancestorFolderIds) {
            try {
                return optionalResolveWithInfo(candidates, resourceType, resourceId, ancestorFolderIds);
            } catch (BusinessException e) {
                return Optional.empty();
            }
        }

        private Optional<Decision> optionalResolveWithInfo(List<PermissionEntity> candidates,
                                                           ResourceType resourceType,
                                                           String resourceId,
                                                           List<String> ancestorFolderIds) {
            List<String> lookupOrder = new ArrayList<>();
            lookupOrder.add(key(resourceType, resourceId));

            List<String> nearestFirst = new ArrayList<>(ancestorFolderIds);
            Collections.reverse(nearestFirst);

            for (String folderId : nearestFirst) {
                lookupOrder.add(key(ResourceType.FOLDER, folderId));
                FolderAccessInfo info = folderInfo.get(folderId);
                if (info != null && !info.inheritFromParent()) {
                    break;
                }
            }

            var byTarget = candidates.stream()
                    .collect(Collectors.groupingBy(p -> key(p.getResourceType(), p.getResourceId())));

            for (String target : lookupOrder) {
                List<PermissionEntity> atTarget = byTarget.get(target);
                if (atTarget == null || atTarget.isEmpty()) continue;

                Optional<PermissionEntity> userPerm = findForType(atTarget, PrincipalType.USER);
                if (userPerm.isPresent()) {
                    PermissionEntity p = userPerm.get();
                    if (p.getPermissionType() == PermissionType.DENY) return Optional.empty();
                    return Optional.of(new Decision(true, p.isCanDownload(), false));
                }
                Optional<PermissionEntity> groupPerm = strongest(atTarget, PrincipalType.GROUP);
                if (groupPerm.isPresent()) {
                    PermissionEntity p = groupPerm.get();
                    if (p.getPermissionType() == PermissionType.DENY) return Optional.empty();
                    return Optional.of(new Decision(true, p.isCanDownload(), false));
                }
            }

            // Base Access 및 워크스페이스 기본 처리
            String closestFolderWithBaseAccess = nearestFirst.stream()
                    .filter(id -> {
                        FolderAccessInfo info = folderInfo.get(id);
                        return info != null && !info.inheritFromParent();
                    })
                    .findFirst()
                    .orElse(null);

            if (closestFolderWithBaseAccess != null) {
                FolderAccessInfo info = folderInfo.get(closestFolderWithBaseAccess);
                if (info != null && info.baseAccess() == PermissionType.DENY) return Optional.empty();
            }

            return Optional.of(new Decision(true, true, false));
        }
    }

    private Decision adminDecision() {
        return new Decision(true, true, true);
    }

    /**
     * 새 판정 로직: Direct DENY→FORBIDDEN, Direct ALLOW→허용,
     * Inherit 순회(inheritFromParent 끊기 처리), Base Access, 워크스페이스 기본 ALLOW.
     */
    private Decision resolveWithDeny(List<PermissionEntity> candidates,
                                     ResourceType resourceType, String resourceId,
                                     List<String> ancestorFolderIds,
                                     List<FolderEntity> ancestorFolders) {
        List<String> lookupOrder = new ArrayList<>();
        lookupOrder.add(key(resourceType, resourceId));

        List<String> nearestFirst = new ArrayList<>(ancestorFolderIds);
        Collections.reverse(nearestFirst);

        Map<String, FolderEntity> folderById = ancestorFolders.stream()
                .collect(Collectors.toMap(FolderEntity::getFolderId, f -> f));

        // 상속 체인: inheritFromParent=false인 폴더까지만 순회
        List<String> inheritChain = new ArrayList<>();
        String baseAccessFolderId = null;
        for (String folderId : nearestFirst) {
            inheritChain.add(folderId);
            FolderEntity folder = folderById.get(folderId);
            if (folder != null && !folder.isInheritFromParent()) {
                baseAccessFolderId = folderId;
                break;
            }
        }
        inheritChain.forEach(id -> lookupOrder.add(key(ResourceType.FOLDER, id)));

        var byTarget = candidates.stream()
                .collect(Collectors.groupingBy(p -> key(p.getResourceType(), p.getResourceId())));

        for (String target : lookupOrder) {
            List<PermissionEntity> atTarget = byTarget.get(target);
            if (atTarget == null || atTarget.isEmpty()) continue;

            Optional<PermissionEntity> userPerm = findForType(atTarget, PrincipalType.USER);
            if (userPerm.isPresent()) {
                return toDecision(userPerm.get());
            }
            Optional<PermissionEntity> groupPerm = strongest(atTarget, PrincipalType.GROUP);
            if (groupPerm.isPresent()) {
                return toDecision(groupPerm.get());
            }
        }

        // Base Access 또는 워크스페이스 기본
        if (baseAccessFolderId != null) {
            FolderEntity folder = folderById.get(baseAccessFolderId);
            if (folder != null && folder.getBaseAccess() == PermissionType.DENY) {
                throw new BusinessException(Code.FORBIDDEN, "이 폴더에 대한 기본 접근이 차단되어 있습니다.");
            }
        }

        // 워크스페이스 기본 = ALLOW
        return new Decision(true, true, false);
    }

    /** 출처 추적 포함 판정. */
    private DecisionWithSource resolveWithSource(List<PermissionEntity> candidates,
                                                 ResourceType resourceType, String resourceId,
                                                 List<String> ancestorFolderIds,
                                                 List<FolderEntity> ancestorFolders) {
        List<String> lookupOrder = new ArrayList<>();
        lookupOrder.add(key(resourceType, resourceId));

        List<String> nearestFirst = new ArrayList<>(ancestorFolderIds);
        Collections.reverse(nearestFirst);

        Map<String, FolderEntity> folderById = ancestorFolders.stream()
                .collect(Collectors.toMap(FolderEntity::getFolderId, f -> f));

        List<String> inheritChain = new ArrayList<>();
        String baseAccessFolderId = null;
        for (String folderId : nearestFirst) {
            inheritChain.add(folderId);
            FolderEntity folder = folderById.get(folderId);
            if (folder != null && !folder.isInheritFromParent()) {
                baseAccessFolderId = folderId;
                break;
            }
        }
        inheritChain.forEach(id -> lookupOrder.add(key(ResourceType.FOLDER, id)));

        var byTarget = candidates.stream()
                .collect(Collectors.groupingBy(p -> key(p.getResourceType(), p.getResourceId())));

        String directKey = key(resourceType, resourceId);

        for (String target : lookupOrder) {
            List<PermissionEntity> atTarget = byTarget.get(target);
            if (atTarget == null || atTarget.isEmpty()) continue;

            boolean isDirect = target.equals(directKey);
            PermissionSource source = isDirect ? PermissionSource.DIRECT : PermissionSource.INHERITED;
            String detail = isDirect ? "직접 부여"
                    : "폴더 '" + target.split(":")[1] + "'에서 상속";

            Optional<PermissionEntity> userPerm = findForType(atTarget, PrincipalType.USER);
            if (userPerm.isPresent()) {
                PermissionEntity p = userPerm.get();
                if (p.getPermissionType() == PermissionType.DENY) {
                    return new DecisionWithSource(new Decision(false, false, false), source, detail);
                }
                return new DecisionWithSource(new Decision(true, p.isCanDownload(), false), source, detail);
            }
            Optional<PermissionEntity> groupPerm = strongest(atTarget, PrincipalType.GROUP);
            if (groupPerm.isPresent()) {
                PermissionEntity p = groupPerm.get();
                if (p.getPermissionType() == PermissionType.DENY) {
                    return new DecisionWithSource(new Decision(false, false, false), source, detail);
                }
                return new DecisionWithSource(new Decision(true, p.isCanDownload(), false), source, detail);
            }
        }

        if (baseAccessFolderId != null) {
            FolderEntity folder = folderById.get(baseAccessFolderId);
            if (folder != null && folder.getBaseAccess() == PermissionType.DENY) {
                return new DecisionWithSource(new Decision(false, false, false),
                        PermissionSource.INHERITED, "폴더 기본 접근 차단");
            }
        }

        return new DecisionWithSource(new Decision(true, true, false),
                PermissionSource.WORKSPACE_DEFAULT, "워크스페이스 기본 허용");
    }

    private Decision toDecision(PermissionEntity permission) {
        if (permission.getPermissionType() == PermissionType.DENY) {
            throw new BusinessException(Code.FORBIDDEN, "접근이 명시적으로 차단되었습니다.");
        }
        return new Decision(true, permission.isCanDownload(), false);
    }

    private Optional<PermissionEntity> findForType(List<PermissionEntity> permissions, PrincipalType type) {
        return permissions.stream()
                .filter(p -> p.getPrincipalType() == type)
                .findFirst();
    }

    private Optional<PermissionEntity> strongest(List<PermissionEntity> permissions, PrincipalType type) {
        // DENY가 있으면 최우선, 없으면 ALLOW 하나를 반환
        List<PermissionEntity> ofType = permissions.stream()
                .filter(p -> p.getPrincipalType() == type)
                .collect(Collectors.toList());
        if (ofType.isEmpty()) return Optional.empty();
        return ofType.stream()
                .filter(p -> p.getPermissionType() == PermissionType.DENY)
                .findFirst()
                .or(() -> ofType.stream().findFirst());
    }

    private String key(ResourceType type, String id) {
        return type.name() + ":" + id;
    }

    @Transactional(readOnly = true)
    public UUID workspaceIdOf(ResourceType resourceType, String resourceId) {
        return loadTarget(resourceType, resourceId).workspaceId();
    }

    private record Target(UUID workspaceId, List<String> ancestorFolderIds) {
    }

    private Target loadTarget(ResourceType resourceType, String resourceId) {
        return switch (resourceType) {
            case FOLDER -> {
                FolderEntity folder = folderRepository.findById(resourceId)
                        .filter(f -> Integer.valueOf(1).equals(f.getUsable()))
                        .orElseThrow(() -> notFound(resourceType));
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

    private List<String> ancestorsOfFolder(String folderId) {
        if (folderId == null) return List.of();
        return folderRepository.findById(folderId)
                .map(FolderEntity::pathSegments)
                .orElse(List.of());
    }

    private List<FolderEntity> loadAncestorFolders(List<String> folderIds) {
        if (folderIds.isEmpty()) return List.of();
        return folderRepository.findAllById(folderIds);
    }

    private List<UUID> groupIdsOf(UUID userId, UUID workspaceId) {
        Set<UUID> memberOf = groupMemberRepository.findByUserId(userId).stream()
                .map(GroupMemberEntity::getGroupId)
                .collect(Collectors.toSet());
        if (memberOf.isEmpty()) return List.of();
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
