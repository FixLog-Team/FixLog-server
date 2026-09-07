package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.security.SecurityUtil;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.FolderEntity;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FolderService {

    /** groupingBy는 null 키를 허용하지 않으므로 루트를 가리키는 대체 키. 폴더 ID로는 쓰이지 않는다. */
    private static final String ROOT_KEY = "";

    private static final Integer ACTIVE = 1;

    /** 형제 정렬 기준. Postgres의 ordinal asc, create_time asc 조회 순서와 동일하게 맞춘다(NULL은 뒤). */
    private static final Comparator<FolderEntity> TREE_ORDER =
            Comparator.comparing(FolderEntity::getOrdinal, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(FolderEntity::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final Comparator<DocumentEntity> DOCUMENT_ORDER =
            Comparator.comparing(DocumentEntity::getOrdinal, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(DocumentEntity::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()));

    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final PermissionResolver permissionResolver;

    public FolderService(FolderRepository folderRepository,
                         DocumentRepository documentRepository,
                         PermissionResolver permissionResolver) {
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.permissionResolver = permissionResolver;
    }

    @Transactional
    public FolderEntity createFolder(String workspaceId, FolderRequest request) {
        if (request.folderName() == null) {
            throw new BusinessException(Code.INVALID_REQUEST, "폴더명은 필수입니다.");
        }

        String userId = SecurityUtil.requireCurrentUserId();
        String targetWorkspaceId;

        if (request.parentId() != null) {
            // 상위 폴더는 접근 가능해야 한다. 워크스페이스도 상위 폴더에서 따라간다.
            FolderEntity parent = requireAccessibleFolder(request.parentId(), userId, "상위 폴더를 찾을 수 없습니다.");
            targetWorkspaceId = parent.getWorkspaceId();
        } else {
            targetWorkspaceId = permissionResolver.resolveWorkspaceId(userId, workspaceId);
        }

        FolderEntity folder = new FolderEntity(
                UUID.randomUUID().toString(),
                targetWorkspaceId,
                request.parentId(),
                request.folderName(),
                nextOrdinal(request.parentId(), targetWorkspaceId),
                userId);
        return folderRepository.save(folder);
    }

    /** 같은 부모 안에서 마지막 폴더 다음 순번. 형제가 없으면 0. */
    private int nextOrdinal(String parentId, String workspaceId) {
        return folderRepository.maxOrdinal(parentId, workspaceId) + 1;
    }

    /**
     * 사이드바용 폴더 트리. 접근 가능한 폴더만 담긴다.
     *
     * <p>접근 불가 폴더는 이름조차 응답에 넣지 않는다. 폴더명 자체가 정보인 경우가 있기 때문이다.
     * 그래서 부모가 접근 불가인데 자식만 접근 가능한 경우, 자식을 가상 루트로 끌어올려 평면 배치하고
     * 숨겨진 조상의 ID도 내려보내지 않는다.
     */
    @Transactional(readOnly = true)
    public List<FolderTreeDto> getFolderTree(String workspaceId) {
        VisibleWorkspace visible = loadVisible(workspaceId);

        Map<String, List<FolderEntity>> childrenByParent = visible.folders().stream()
                .collect(Collectors.groupingBy(folder -> visible.parentKeyOf(folder)));

        Map<String, Long> documentCounts = new HashMap<>();
        for (DocumentEntity document : visible.documents()) {
            String key = visible.folderKeyOf(document);
            if (!ROOT_KEY.equals(key)) {
                documentCounts.merge(key, 1L, Long::sum);
            }
        }

        return buildNodes(ROOT_KEY, childrenByParent, documentCounts);
    }

    private List<FolderTreeDto> buildNodes(String parentKey,
                                           Map<String, List<FolderEntity>> childrenByParent,
                                           Map<String, Long> documentCounts) {
        // 숨겨진 조상을 노출하지 않기 위해, 실제 parent_id가 아니라 트리에서의 위치를 내려보낸다.
        String reportedParentId = ROOT_KEY.equals(parentKey) ? null : parentKey;

        return childrenByParent.getOrDefault(parentKey, List.of()).stream()
                .sorted(TREE_ORDER)
                .map(folder -> new FolderTreeDto(
                        folder.getFolderId(),
                        reportedParentId,
                        folder.getFolderName(),
                        folder.getOrdinal(),
                        documentCounts.getOrDefault(folder.getFolderId(), 0L),
                        buildNodes(folder.getFolderId(), childrenByParent, documentCounts)))
                .toList();
    }

    @Transactional(readOnly = true)
    public FolderEntity getFolder(String folderId) {
        return requireAccessibleFolder(folderId, SecurityUtil.requireCurrentUserId(),
                "폴더를 찾을 수 없습니다.");
    }

    @Transactional
    public FolderEntity updateFolder(String folderId, FolderRequest request) {
        String userId = SecurityUtil.requireCurrentUserId();
        FolderEntity folder = requireAccessibleFolder(folderId, userId, "폴더를 찾을 수 없습니다.");

        // 부모 변경은 moveFolder, 순서 변경은 reorderFolders에서만 처리한다.
        folder.rename(request.folderName(), userId);
        return folderRepository.save(folder);
    }

    /**
     * 같은 부모 아래 폴더들의 순서를 folderIds 순서대로 다시 매긴다.
     * folderIds는 화면에 보이는 형제 전체와 정확히 일치해야 한다. 일부만 보내면 순번에 구멍이나 중복이 생긴다.
     */
    @Transactional
    public List<FolderEntity> reorderFolders(String workspaceId, String parentId, List<String> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            throw new BusinessException(Code.INVALID_REQUEST, "정렬할 폴더 목록이 비어 있습니다.");
        }

        Set<String> requested = new LinkedHashSet<>(folderIds);
        if (requested.size() != folderIds.size()) {
            throw new BusinessException(Code.INVALID_REQUEST, "폴더 목록에 중복된 항목이 있습니다.");
        }

        VisibleWorkspace visible = loadVisible(workspaceId);
        if (parentId != null && !visible.visibleFolderIds().contains(parentId)) {
            throw new BusinessException(Code.NOT_FOUND, "상위 폴더를 찾을 수 없습니다.");
        }

        // 가상 루트로 끌어올려진 폴더도 화면에서는 형제로 보이므로, 트리 위치를 기준으로 판단한다.
        String parentKey = parentId == null ? ROOT_KEY : parentId;
        List<FolderEntity> current = visible.folders().stream()
                .filter(folder -> parentKey.equals(visible.parentKeyOf(folder)))
                .sorted(TREE_ORDER)
                .toList();

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
            folder.applyOrdinal(ordinal++);
            reordered.add(folder);
        }
        return folderRepository.saveAll(reordered);
    }

    @Transactional
    public FolderEntity moveFolder(String folderId, String newParentId) {
        String userId = SecurityUtil.requireCurrentUserId();
        FolderEntity folder = requireAccessibleFolder(folderId, userId, "폴더를 찾을 수 없습니다.");

        if (newParentId != null) {
            if (newParentId.equals(folderId)) {
                throw new BusinessException(Code.INVALID_REQUEST, "폴더를 자기 자신으로 이동할 수 없습니다.");
            }
            FolderEntity newParent = requireAccessibleFolder(newParentId, userId, "이동할 상위 폴더를 찾을 수 없습니다.");
            if (!newParent.getWorkspaceId().equals(folder.getWorkspaceId())) {
                throw new BusinessException(Code.INVALID_REQUEST, "다른 워크스페이스로는 이동할 수 없습니다.");
            }
            if (isDescendantOf(folderId, newParentId, folder.getWorkspaceId())) {
                throw new BusinessException(Code.INVALID_REQUEST, "폴더를 자신의 하위 폴더로 이동할 수 없습니다.");
            }
        }

        // 새 부모의 형제들과 순번이 겹치지 않도록 맨 끝으로 보낸다.
        folder.moveTo(newParentId, nextOrdinal(newParentId, folder.getWorkspaceId()), userId);
        return folderRepository.save(folder);
    }

    @Transactional
    public void deleteFolder(String folderId) {
        String userId = SecurityUtil.requireCurrentUserId();
        FolderEntity folder = requireAccessibleFolder(folderId, userId, "폴더를 찾을 수 없습니다.");

        String workspaceId = folder.getWorkspaceId();
        PermissionSnapshot snapshot = permissionResolver.snapshot(workspaceId, userId);

        // 대상 폴더와 모든 하위 폴더 트리를 수집한다.
        List<FolderEntity> subtree = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        subtree.add(folder);
        queue.add(folder.getFolderId());
        while (!queue.isEmpty()) {
            List<FolderEntity> children = folderRepository
                    .findByParentIdAndWorkspaceIdAndUsableOrderByOrdinalAscCreateTimeAsc(
                            queue.poll(), workspaceId, ACTIVE);
            for (FolderEntity child : children) {
                subtree.add(child);
                queue.add(child.getFolderId());
            }
        }

        // 폴더 트리와 그 안의 문서를 모두 소프트 삭제한다.
        List<DocumentEntity> documents = new ArrayList<>();
        for (FolderEntity target : subtree) {
            // 볼 수 없는 콘텐츠를 삭제해 버리는 일이 없도록, 하나라도 접근 불가면 전체를 거절한다.
            if (!snapshot.isAllowed(target)) {
                throw new BusinessException(Code.FORBIDDEN,
                        "접근할 수 없는 하위 폴더가 포함되어 있어 삭제할 수 없습니다.");
            }
            for (DocumentEntity document : documentRepository
                    .findByFolderIdAndWorkspaceIdAndUsableOrderByOrdinalAscCreateTimeAsc(
                            target.getFolderId(), workspaceId, ACTIVE)) {
                if (!snapshot.isAllowed(document)) {
                    throw new BusinessException(Code.FORBIDDEN,
                            "접근할 수 없는 문서가 포함되어 있어 삭제할 수 없습니다.");
                }
                documents.add(document);
            }
        }

        subtree.forEach(target -> target.softDelete(userId));
        documents.forEach(document -> document.softDelete(userId));
        folderRepository.saveAll(subtree);
        documentRepository.saveAll(documents);
    }

    private boolean isDescendantOf(String ancestorId, String candidateId, String workspaceId) {
        String current = candidateId;
        int guard = 0;
        while (current != null && guard++ < 10000) {
            if (current.equals(ancestorId)) {
                return true;
            }
            current = folderRepository.findByFolderIdAndUsable(current, ACTIVE)
                    .filter(folder -> workspaceId.equals(folder.getWorkspaceId()))
                    .map(FolderEntity::getParentId)
                    .orElse(null);
        }
        return false;
    }

    @Transactional(readOnly = true)
    public FolderContentsDto getRootContents(String workspaceId) {
        VisibleWorkspace visible = loadVisible(workspaceId);
        return contentsOf(ROOT_KEY, visible);
    }

    @Transactional(readOnly = true)
    public FolderContentsDto getFolderContents(String workspaceId, String folderId) {
        VisibleWorkspace visible = loadVisible(workspaceId);
        if (!visible.visibleFolderIds().contains(folderId)) {
            throw new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다.");
        }
        return contentsOf(folderId, visible);
    }

    private FolderContentsDto contentsOf(String parentKey, VisibleWorkspace visible) {
        List<FolderDto> folders = visible.folders().stream()
                .filter(folder -> parentKey.equals(visible.parentKeyOf(folder)))
                .sorted(TREE_ORDER)
                .map(FolderDto::from)
                .toList();

        List<DocumentDto> documents = visible.documents().stream()
                .filter(document -> parentKey.equals(visible.folderKeyOf(document)))
                .sorted(DOCUMENT_ORDER)
                .map(DocumentDto::from)
                .toList();

        return new FolderContentsDto(folders, documents);
    }

    private FolderEntity requireAccessibleFolder(String folderId, String userId, String notFoundMessage) {
        FolderEntity folder = folderRepository.findByFolderIdAndUsable(folderId, ACTIVE)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, notFoundMessage));
        if (!permissionResolver.snapshot(folder.getWorkspaceId(), userId).isAllowed(folder)) {
            // 접근 불가 폴더는 존재 자체를 알리지 않는다.
            throw new BusinessException(Code.NOT_FOUND, notFoundMessage);
        }
        return folder;
    }

    private VisibleWorkspace loadVisible(String workspaceId) {
        String userId = SecurityUtil.requireCurrentUserId();
        String resolvedWorkspaceId = permissionResolver.resolveWorkspaceId(userId, workspaceId);
        PermissionSnapshot snapshot = permissionResolver.snapshot(resolvedWorkspaceId, userId);

        List<FolderEntity> folders = folderRepository
                .findByWorkspaceIdAndUsable(resolvedWorkspaceId, ACTIVE).stream()
                .filter(snapshot::isAllowed)
                .toList();

        // 문서 수를 GROUP BY로 세면 접근 불가 문서의 존재가 개수로 새어 나간다.
        // 판정을 통과한 문서만 세기 위해 워크스페이스의 문서를 한 번에 읽어 메모리에서 집계한다.
        List<DocumentEntity> documents = documentRepository
                .findByWorkspaceIdAndUsable(resolvedWorkspaceId, ACTIVE).stream()
                .filter(snapshot::isAllowed)
                .toList();

        Set<String> visibleFolderIds = new HashSet<>();
        folders.forEach(folder -> visibleFolderIds.add(folder.getFolderId()));

        return new VisibleWorkspace(folders, documents, visibleFolderIds);
    }

    /**
     * 한 사용자에게 보이는 워크스페이스의 모습.
     * 숨겨진 조상 아래 있던 노드는 트리에서 루트로 끌어올려진다.
     */
    private record VisibleWorkspace(List<FolderEntity> folders,
                                    List<DocumentEntity> documents,
                                    Set<String> visibleFolderIds) {

        /** 트리에서 이 폴더가 매달릴 위치. 부모가 보이지 않으면 루트로 승격된다. */
        String parentKeyOf(FolderEntity folder) {
            return keyOf(folder.getParentId());
        }

        /** 트리에서 이 문서가 매달릴 위치. 폴더가 보이지 않으면 루트로 승격된다. */
        String folderKeyOf(DocumentEntity document) {
            return keyOf(document.getFolderId());
        }

        private String keyOf(String parentId) {
            return parentId == null || !visibleFolderIds.contains(parentId) ? ROOT_KEY : parentId;
        }
    }
}
