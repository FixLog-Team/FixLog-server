package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.PermissionOverrideRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.application.repository.WorkspaceRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.security.SecurityUtil;
import com.fixlog.domain.model.AccessEffect;
import com.fixlog.domain.model.BaseAccess;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.domain.model.NodeType;
import com.fixlog.domain.model.PermissionOverrideEntity;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.WorkspaceEntity;
import com.fixlog.presentation.dto.response.NodeOverrideDto;
import com.fixlog.presentation.dto.response.NodePermissionDto;
import com.fixlog.presentation.dto.response.UserAccessDto;
import com.fixlog.presentation.dto.response.UserAccessPageDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 콘텐츠 권한의 변경과 조회. 권한 변경은 이 서비스에서만 이루어진다.
 *
 * <p>조회는 판정을 다시 계산하지 않고 {@link PermissionResolver}를 그대로 통과시킨다.
 * 화면에 표시된 권한과 실제 접근 결과가 어긋나지 않게 하기 위한 것이다.
 */
@Service
public class PermissionAdminService {

    private static final Integer ACTIVE = 1;

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final PermissionOverrideRepository overrideRepository;
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final PermissionResolver permissionResolver;

    public PermissionAdminService(WorkspaceRepository workspaceRepository,
                                  WorkspaceMemberRepository memberRepository,
                                  PermissionOverrideRepository overrideRepository,
                                  FolderRepository folderRepository,
                                  DocumentRepository documentRepository,
                                  UserRepository userRepository,
                                  PermissionResolver permissionResolver) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.overrideRepository = overrideRepository;
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.permissionResolver = permissionResolver;
    }

    @Transactional(readOnly = true)
    public NodePermissionDto getNodePermission(NodeType nodeType, String nodeId) {
        NodeInfo node = requireManageableNode(nodeType, nodeId);

        List<PermissionOverrideEntity> overrides = overrideRepository.findByNodeId(nodeId);
        Map<UUID, UserEntity> usersById = loadUsers(
                overrides.stream().map(PermissionOverrideEntity::getUserId).toList());

        return new NodePermissionDto(
                node.type(),
                node.nodeId(),
                node.name(),
                node.workspaceId(),
                node.baseAccess(),
                overrides.stream()
                        .map(override -> NodeOverrideDto.of(override, usersById.get(override.getUserId())))
                        .toList());
    }

    /**
     * 노드의 기본 접근 정책을 바꾼다. INHERIT을 주면 다시 부모를 따른다.
     */
    @Transactional
    public NodePermissionDto changeBaseAccess(NodeType nodeType, String nodeId, BaseAccess baseAccess) {
        requireManageableNode(nodeType, nodeId);
        String updateUser = SecurityUtil.requireCurrentUserId();

        switch (nodeType) {
            case WORKSPACE -> {
                WorkspaceEntity workspace = requireWorkspace(nodeId);
                // 루트에 INHERIT을 주는 요청은 엔티티에서 거절된다.
                workspace.changeBaseAccess(baseAccess, updateUser);
                workspaceRepository.save(workspace);
            }
            case FOLDER -> {
                FolderEntity folder = requireFolder(nodeId);
                folder.changeBaseAccess(baseAccess, updateUser);
                folderRepository.save(folder);
            }
            case DOCUMENT -> {
                DocumentEntity document = requireDocument(nodeId);
                document.changeBaseAccess(baseAccess, updateUser);
                documentRepository.save(document);
            }
        }
        return getNodePermission(nodeType, nodeId);
    }

    /**
     * 특정 사용자에게 이 노드의 접근 여부를 직접 지정한다.
     * 키가 (노드, 사용자)이므로 같은 노드에 상충하는 설정이 동시에 존재할 수 없다.
     */
    @Transactional
    public NodePermissionDto upsertOverride(NodeType nodeType, String nodeId,
                                            String targetUserId, AccessEffect effect) {
        NodeInfo node = requireManageableNode(nodeType, nodeId);
        UUID target = requireMember(node.workspaceId(), targetUserId);
        String updateUser = SecurityUtil.requireCurrentUserId();

        overrideRepository.findByNodeIdAndUserId(nodeId, target)
                .ifPresentOrElse(
                        existing -> {
                            existing.changeEffect(effect, updateUser);
                            overrideRepository.save(existing);
                        },
                        () -> overrideRepository.save(new PermissionOverrideEntity(
                                nodeId, target, node.type(), node.workspaceId(), effect, updateUser))
                );
        return getNodePermission(nodeType, nodeId);
    }

    /** 개별 설정을 지운다. 해당 사용자는 이 노드에서 다시 상속을 따른다. */
    @Transactional
    public NodePermissionDto removeOverride(NodeType nodeType, String nodeId, String targetUserId) {
        NodeInfo node = requireManageableNode(nodeType, nodeId);
        UUID target = toUuid(targetUserId);

        if (overrideRepository.findByNodeIdAndUserId(nodeId, target).isEmpty()) {
            throw new BusinessException(Code.NOT_FOUND, "해당 사용자의 개별 설정이 없습니다.");
        }
        overrideRepository.deleteByNodeIdAndUserId(nodeId, target);
        return getNodePermission(node.type(), node.nodeId());
    }

    /**
     * 한 사용자가 워크스페이스의 각 노드에 대해 갖는 최종 권한과 그 출처.
     *
     * <p>노드 수가 많지 않은 전제(결정서 §5.4의 캐싱 도입 기준 1만 개 미만)에서
     * 판정 결과를 메모리에서 정렬 · 페이지네이션한다. 판정 자체는 스냅샷 하나를 재사용한다.
     */
    @Transactional(readOnly = true)
    public UserAccessPageDto listUserAccess(String targetUserId, String workspaceId,
                                            NodeType nodeTypeFilter, int page, int size) {
        String callerId = SecurityUtil.requireCurrentUserId();
        String resolvedWorkspaceId = permissionResolver.resolveWorkspaceId(callerId, workspaceId);
        permissionResolver.requireManageRole(callerId, resolvedWorkspaceId);
        requireMember(resolvedWorkspaceId, targetUserId);

        PermissionSnapshot snapshot = permissionResolver.snapshot(resolvedWorkspaceId, targetUserId);
        List<UserAccessDto> rows = new ArrayList<>();

        if (nodeTypeFilter == null || nodeTypeFilter == NodeType.FOLDER) {
            folderRepository.findByWorkspaceIdAndUsable(resolvedWorkspaceId, ACTIVE).stream()
                    .sorted(Comparator.comparing(FolderEntity::getFolderName,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(folder -> rows.add(UserAccessDto.of(
                            NodeType.FOLDER, folder.getFolderId(), folder.getFolderName(),
                            folder.getParentId(), folder.getBaseAccess(), snapshot.decide(folder))));
        }

        if (nodeTypeFilter == null || nodeTypeFilter == NodeType.DOCUMENT) {
            documentRepository.findByWorkspaceIdAndUsable(resolvedWorkspaceId, ACTIVE).stream()
                    .sorted(Comparator.comparing(DocumentEntity::getTitle,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(document -> rows.add(UserAccessDto.of(
                            NodeType.DOCUMENT, document.getDocumentId(), document.getTitle(),
                            document.getFolderId(), document.getBaseAccess(), snapshot.decide(document))));
        }

        return UserAccessPageDto.of(rows, page, size);
    }

    /** 노드를 찾고, 호출자가 그 워크스페이스의 권한을 관리할 수 있는지 확인한다. */
    private NodeInfo requireManageableNode(NodeType nodeType, String nodeId) {
        NodeInfo node = loadNodeInfo(nodeType, nodeId);
        permissionResolver.requireManageRole(SecurityUtil.requireCurrentUserId(), node.workspaceId());
        return node;
    }

    private NodeInfo loadNodeInfo(NodeType nodeType, String nodeId) {
        return switch (nodeType) {
            case WORKSPACE -> {
                WorkspaceEntity workspace = requireWorkspace(nodeId);
                yield new NodeInfo(NodeType.WORKSPACE, workspace.getWorkspaceId(),
                        workspace.getWorkspaceName(), workspace.getWorkspaceId(), workspace.getBaseAccess());
            }
            case FOLDER -> {
                FolderEntity folder = requireFolder(nodeId);
                yield new NodeInfo(NodeType.FOLDER, folder.getFolderId(), folder.getFolderName(),
                        requireWorkspaceId(folder.getWorkspaceId()), folder.getBaseAccess());
            }
            case DOCUMENT -> {
                DocumentEntity document = requireDocument(nodeId);
                yield new NodeInfo(NodeType.DOCUMENT, document.getDocumentId(), document.getTitle(),
                        requireWorkspaceId(document.getWorkspaceId()), document.getBaseAccess());
            }
        };
    }

    private WorkspaceEntity requireWorkspace(String workspaceId) {
        return workspaceRepository.findByWorkspaceIdAndUsable(workspaceId, ACTIVE)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "워크스페이스를 찾을 수 없습니다."));
    }

    private FolderEntity requireFolder(String folderId) {
        return folderRepository.findByFolderIdAndUsable(folderId, ACTIVE)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));
    }

    private DocumentEntity requireDocument(String documentId) {
        return documentRepository.findByDocumentIdAndUsable(documentId, ACTIVE)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "문서를 찾을 수 없습니다."));
    }

    private String requireWorkspaceId(String workspaceId) {
        if (workspaceId == null) {
            throw new BusinessException(Code.INVALID_REQUEST,
                    "워크스페이스가 지정되지 않은 노드입니다. 권한 마이그레이션이 필요합니다.");
        }
        return workspaceId;
    }

    /** 소속되지 않은 사용자에게 개별 설정을 거는 것은 의미가 없으므로 막는다. */
    private UUID requireMember(String workspaceId, String userId) {
        UUID target = toUuid(userId);
        if (memberRepository.findByWorkspaceIdAndUserId(workspaceId, target).isEmpty()) {
            throw new BusinessException(Code.INVALID_REQUEST, "해당 워크스페이스의 멤버가 아닙니다.");
        }
        return target;
    }

    private Map<UUID, UserEntity> loadUsers(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getUserId, Function.identity()));
    }

    private UUID toUuid(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(Code.INVALID_REQUEST, "사용자 식별자 형식이 올바르지 않습니다.");
        }
    }

    /** 폴더 · 문서 · 워크스페이스를 같은 방식으로 다루기 위한 내부 표현. */
    private record NodeInfo(NodeType type, String nodeId, String name,
                            String workspaceId, BaseAccess baseAccess) {}
}
