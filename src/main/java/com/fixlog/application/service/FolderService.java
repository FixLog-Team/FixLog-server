package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.security.SecurityUtil;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.domain.model.PermissionAction;
import com.fixlog.domain.model.ResourceType;
import com.fixlog.presentation.dto.request.FolderRequest;
import com.fixlog.presentation.dto.response.DocumentDto;
import com.fixlog.presentation.dto.response.FolderContentsDto;
import com.fixlog.presentation.dto.response.FolderDto;
import com.fixlog.presentation.dto.response.FolderTreeDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 폴더 서비스.
 *
 * <p>조회 범위는 현재 워크스페이스로 한정되고(FR-PRM-009), 접근 여부는
 * {@link PermissionEvaluator}가 판정한다. {@code create_user}는 작성자 정보로만 남는다.
 */
@Service
public class FolderService {

    /** groupingBy는 null 키를 허용하지 않으므로 루트를 가리키는 대체 키. 폴더 ID로는 쓰이지 않는다. */
    private static final String ROOT_KEY = "";

    /** 형제 정렬 기준. Postgres의 ordinal asc, create_time asc 조회 순서와 동일하게 맞춘다(NULL은 뒤). */
    private static final Comparator<FolderEntity> TREE_ORDER =
            Comparator.comparing(FolderEntity::getOrdinal, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(FolderEntity::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()));

    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final WorkspaceContext workspaceContext;
    private final PermissionEvaluator permissionEvaluator;

    public FolderService(FolderRepository folderRepository,
                         DocumentRepository documentRepository,
                         WorkspaceContext workspaceContext,
                         PermissionEvaluator permissionEvaluator) {
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.workspaceContext = workspaceContext;
        this.permissionEvaluator = permissionEvaluator;
    }

    @Transactional
    public FolderEntity createFolder(FolderRequest request) {
        if (request.folderName() == null) {
            throw new BusinessException(Code.INVALID_REQUEST, "폴더명은 필수입니다.");
        }

        String userId = requireUserId();
        UUID workspaceId = workspaceContext.requireCurrentWorkspaceId();

        // 하위에 만들려면 그 폴더를 편집할 수 있어야 한다. 루트는 워크스페이스 구성원이면 된다.
        if (request.parentId() != null) {
            permissionEvaluator.require(ResourceType.FOLDER, request.parentId(), PermissionAction.EDIT);
        }

        String folderId = UUID.randomUUID().toString();
        FolderEntity folder = new FolderEntity(
                folderId,
                workspaceId,
                request.parentId(),
                request.folderName(),
                nextOrdinal(request.parentId(), workspaceId),
                userId,
                parentPath(request.parentId(), workspaceId));
        return folderRepository.save(folder);
    }

    /** 같은 부모 안에서 마지막 폴더 다음 순번. 형제가 없으면 0. */
    private int nextOrdinal(String parentId, UUID workspaceId) {
        return folderRepository.maxOrdinal(parentId, workspaceId) + 1;
    }

    /** 루트 아래면 "/", 아니면 부모의 경로. */
    private String parentPath(String parentId, UUID workspaceId) {
        if (parentId == null) {
            return "/";
        }
        return loadInWorkspace(parentId, workspaceId).getPath();
    }

    private List<FolderEntity> siblings(String parentId, UUID workspaceId) {
        return parentId == null
                ? folderRepository.findByWorkspaceIdAndParentIdIsNullAndUsableOrderByOrdinalAscCreateTimeAsc(
                        workspaceId, Integer.valueOf(1))
                : folderRepository.findByWorkspaceIdAndParentIdAndUsableOrderByOrdinalAscCreateTimeAsc(
                        workspaceId, parentId, Integer.valueOf(1));
    }

    /**
     * 사이드바용 전체 폴더 트리. 폴더 전량과 폴더별 문서 수를 각각 한 번씩 조회한 뒤 메모리에서 엮는다.
     * 볼 수 없는 폴더는 트리에서 빠진다.
     */
    @Transactional(readOnly = true)
    public List<FolderTreeDto> getFolderTree() {
        UUID workspaceId = workspaceContext.requireCurrentWorkspaceId();
        PermissionEvaluator.Scope scope = permissionEvaluator.scopeFor(workspaceId);

        List<FolderEntity> folders = folderRepository
                .findByWorkspaceIdAndUsable(workspaceId, Integer.valueOf(1)).stream()
                .filter(folder -> scope.canViewFolder(folder.getFolderId()))
                .toList();

        Map<String, Long> documentCounts = documentRepository.countDocumentsByFolder(workspaceId).stream()
                .collect(Collectors.toMap(
                        DocumentRepository.FolderDocumentCount::getFolderId,
                        DocumentRepository.FolderDocumentCount::getDocumentCount));

        // 루트 폴더는 parentId가 null이므로 groupingBy에 넣을 수 없다. 빈 문자열을 루트 키로 쓴다.
        Map<String, List<FolderEntity>> childrenByParent = folders.stream()
                .collect(Collectors.groupingBy(f -> f.getParentId() == null ? ROOT_KEY : f.getParentId()));

        return buildNodes(ROOT_KEY, childrenByParent, documentCounts);
    }

    private List<FolderTreeDto> buildNodes(String parentKey,
                                           Map<String, List<FolderEntity>> childrenByParent,
                                           Map<String, Long> documentCounts) {
        return childrenByParent.getOrDefault(parentKey, List.of()).stream()
                .sorted(TREE_ORDER)
                .map(folder -> new FolderTreeDto(
                        folder.getFolderId(),
                        folder.getParentId(),
                        folder.getFolderName(),
                        folder.getOrdinal(),
                        documentCounts.getOrDefault(folder.getFolderId(), 0L),
                        buildNodes(folder.getFolderId(), childrenByParent, documentCounts)))
                .toList();
    }

    @Transactional(readOnly = true)
    public FolderEntity getFolder(String folderId) {
        return loadPermitted(folderId, PermissionAction.VIEW);
    }

    @Transactional
    public FolderEntity updateFolder(String folderId, FolderRequest request) {
        FolderEntity folder = loadPermitted(folderId, PermissionAction.EDIT);

        // 부모 변경은 moveFolder, 순서 변경은 reorderFolders에서만 처리한다.
        folder.rename(request.folderName(), requireUserId());
        return folderRepository.save(folder);
    }

    /**
     * 같은 부모 아래 폴더들의 순서를 folderIds 순서대로 다시 매긴다.
     * folderIds는 해당 부모의 활성 폴더 전체와 정확히 일치해야 한다. 일부만 보내면 순번에 구멍이나 중복이 생긴다.
     */
    @Transactional
    public List<FolderEntity> reorderFolders(String parentId, List<String> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            throw new BusinessException(Code.INVALID_REQUEST, "정렬할 폴더 목록이 비어 있습니다.");
        }

        Set<String> requested = new LinkedHashSet<>(folderIds);
        if (requested.size() != folderIds.size()) {
            throw new BusinessException(Code.INVALID_REQUEST, "폴더 목록에 중복된 항목이 있습니다.");
        }

        // 부모가 지정되면 그 폴더가 속한 워크스페이스를, 루트 정렬이면 현재 컨텍스트를 기준으로 한다.
        UUID workspaceId;
        if (parentId != null) {
            permissionEvaluator.require(ResourceType.FOLDER, parentId, PermissionAction.EDIT);
            workspaceId = loadPermitted(parentId, PermissionAction.EDIT).getWorkspaceId();
        } else {
            workspaceId = workspaceContext.requireCurrentWorkspaceId();
        }

        List<FolderEntity> current = siblings(parentId, workspaceId);
        if (current.size() != requested.size()) {
            throw new BusinessException(Code.INVALID_REQUEST, "해당 폴더의 하위 폴더 전체를 순서대로 보내야 합니다.");
        }

        Map<String, FolderEntity> byId = current.stream()
                .collect(Collectors.toMap(FolderEntity::getFolderId, Function.identity()));

        List<FolderEntity> reordered = new ArrayList<>();
        int ordinal = 0;
        for (String folderId : requested) {
            FolderEntity folder = byId.get(folderId);
            if (folder == null) {
                throw new BusinessException(Code.INVALID_REQUEST, "해당 위치에 속하지 않은 폴더가 포함되어 있습니다.");
            }
            permissionEvaluator.require(ResourceType.FOLDER, folderId, PermissionAction.EDIT);
            folder.applyOrdinal(ordinal++);
            reordered.add(folder);
        }
        return folderRepository.saveAll(reordered);
    }

    @Transactional
    public FolderEntity moveFolder(String folderId, String newParentId) {
        FolderEntity folder = loadPermitted(folderId, PermissionAction.EDIT);
        // 기준 워크스페이스는 요청 컨텍스트가 아니라 대상 폴더가 속한 곳이다.
        // 컨텍스트를 쓰면 다른 워크스페이스의 형제들과 순번을 겨루게 된다.
        UUID workspaceId = folder.getWorkspaceId();

        if (newParentId != null) {
            if (newParentId.equals(folderId)) {
                throw new BusinessException(Code.INVALID_REQUEST, "폴더를 자기 자신으로 이동할 수 없습니다.");
            }
            permissionEvaluator.require(ResourceType.FOLDER, newParentId, PermissionAction.EDIT);
            loadInWorkspace(newParentId, workspaceId);
            if (isDescendantOf(folderId, newParentId, workspaceId)) {
                throw new BusinessException(Code.INVALID_REQUEST, "폴더를 자신의 하위 폴더로 이동할 수 없습니다.");
            }
        }

        // 새 부모의 형제들과 순번이 겹치지 않도록 맨 끝으로 보낸다.
        folder.moveTo(newParentId, nextOrdinal(newParentId, workspaceId), requireUserId());
        repath(folder, parentPath(newParentId, workspaceId));
        return folderRepository.save(folder);
    }

    /**
     * 폴더를 옮기면 그 아래 모든 폴더의 경로도 함께 바뀐다. 하나라도 빠지면 권한 상속이
     * 옛 조상을 따라가므로, 서브트리 일괄 갱신은 반드시 이 메서드 하나를 통한다 (R-04).
     */
    private void repath(FolderEntity folder, String newParentPath) {
        String oldPath = folder.getPath();
        String newPath = FolderEntity.pathUnder(newParentPath, folder.getFolderId());
        if (newPath.equals(oldPath)) {
            return;
        }

        List<FolderEntity> subtree = folderRepository.findByPathStartingWith(oldPath);
        for (FolderEntity node : subtree) {
            node.applyPath(newPath + node.getPath().substring(oldPath.length()));
        }
        folderRepository.saveAll(subtree);
        folder.applyPath(newPath);
    }

    @Transactional
    public void deleteFolder(String folderId) {
        FolderEntity folder = loadPermitted(folderId, PermissionAction.DELETE);
        UUID workspaceId = folder.getWorkspaceId();
        String userId = requireUserId();

        // 대상 폴더와 모든 하위 폴더 트리를 수집한다.
        List<FolderEntity> subtree = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        subtree.add(folder);
        queue.add(folder.getFolderId());
        while (!queue.isEmpty()) {
            List<FolderEntity> children = folderRepository
                    .findByWorkspaceIdAndParentIdAndUsableOrderByOrdinalAscCreateTimeAsc(
                            workspaceId, queue.poll(), Integer.valueOf(1));
            for (FolderEntity child : children) {
                subtree.add(child);
                queue.add(child.getFolderId());
            }
        }

        // 폴더 트리와 그 안의 문서를 모두 소프트 삭제한다.
        List<DocumentEntity> documents = new ArrayList<>();
        for (FolderEntity f : subtree) {
            f.softDelete(userId);
            for (DocumentEntity doc : documentRepository
                    .findByWorkspaceIdAndFolderIdAndUsableOrderByOrdinalAscCreateTimeAsc(
                            workspaceId, f.getFolderId(), Integer.valueOf(1))) {
                doc.softDelete(userId);
                documents.add(doc);
            }
        }
        folderRepository.saveAll(subtree);
        documentRepository.saveAll(documents);
    }

    private boolean isDescendantOf(String ancestorId, String candidateId, UUID workspaceId) {
        String current = candidateId;
        int guard = 0;
        while (current != null && guard++ < 10000) {
            if (current.equals(ancestorId)) {
                return true;
            }
            current = folderRepository.findByFolderIdAndUsable(current, Integer.valueOf(1))
                    .filter(f -> f.getWorkspaceId().equals(workspaceId))
                    .map(FolderEntity::getParentId)
                    .orElse(null);
        }
        return false;
    }

    @Transactional(readOnly = true)
    public FolderContentsDto getRootContents() {
        UUID workspaceId = workspaceContext.requireCurrentWorkspaceId();
        PermissionEvaluator.Scope scope = permissionEvaluator.scopeFor(workspaceId);

        List<FolderDto> folders = siblings(null, workspaceId).stream()
                .filter(f -> scope.canViewFolder(f.getFolderId()))
                .map(FolderDto::from).toList();

        List<DocumentDto> documents = documentRepository
                .findByWorkspaceIdAndFolderIdIsNullAndUsableOrderByOrdinalAscCreateTimeAsc(
                        workspaceId, Integer.valueOf(1))
                .stream()
                .filter(d -> scope.canViewDocument(d.getDocumentId(), d.getFolderId()))
                .map(DocumentDto::from).toList();

        return new FolderContentsDto(folders, documents);
    }

    @Transactional(readOnly = true)
    public FolderContentsDto getFolderContents(String folderId) {
        FolderEntity folder = loadPermitted(folderId, PermissionAction.VIEW);
        UUID workspaceId = folder.getWorkspaceId();
        PermissionEvaluator.Scope scope = permissionEvaluator.scopeFor(workspaceId);

        List<FolderDto> folders = siblings(folderId, workspaceId).stream()
                .filter(f -> scope.canViewFolder(f.getFolderId()))
                .map(FolderDto::from).toList();

        List<DocumentDto> documents = documentRepository
                .findByWorkspaceIdAndFolderIdAndUsableOrderByOrdinalAscCreateTimeAsc(
                        workspaceId, folderId, Integer.valueOf(1))
                .stream()
                .filter(d -> scope.canViewDocument(d.getDocumentId(), d.getFolderId()))
                .map(DocumentDto::from).toList();

        return new FolderContentsDto(folders, documents);
    }

    private FolderEntity loadPermitted(String folderId, PermissionAction action) {
        permissionEvaluator.require(ResourceType.FOLDER, folderId, action);
        return folderRepository.findByFolderIdAndUsable(folderId, Integer.valueOf(1))
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));
    }

    /** 워크스페이스 밖의 폴더는 존재하지 않는 것으로 취급한다. */
    private FolderEntity loadInWorkspace(String folderId, UUID workspaceId) {
        return folderRepository.findByFolderIdAndUsable(folderId, Integer.valueOf(1))
                .filter(f -> f.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "상위 폴더를 찾을 수 없습니다."));
    }

    private String requireUserId() {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(Code.UNAUTHORIZED, "인증 정보가 없습니다.");
        }
        return userId;
    }
}
