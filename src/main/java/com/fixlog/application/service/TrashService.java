package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentFavoriteRepository;
import com.fixlog.application.repository.DocumentHistoryRepository;
import com.fixlog.application.repository.DocumentLabelRepository;
import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.PermissionRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.AuditAction;
import com.fixlog.domain.model.AuditResult;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.domain.model.ResourceType;
import com.fixlog.domain.model.WorkspaceMemberEntity;
import com.fixlog.presentation.dto.response.TrashItemDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 휴지통 (FR-TRS-001~008).
 *
 * <p>삭제된 항목에는 <b>권한 상속을 다시 태우지 않는다.</b> 조상 폴더가 함께 삭제됐거나 경로가
 * 끊긴 상태라 판정이 성립하지 않기 때문이다. 대신 <b>지운 본인과 워크스페이스 관리자</b>만
 * 보고 되돌릴 수 있게 한다. 되돌린 뒤에는 원래 권한 규칙이 그대로 적용된다.
 */
@Service
public class TrashService {

    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final DocumentHistoryRepository historyRepository;
    private final DocumentLabelRepository documentLabelRepository;
    private final DocumentFavoriteRepository documentFavoriteRepository;
    private final PermissionRepository permissionRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceContext workspaceContext;
    private final AuditService auditService;

    public TrashService(DocumentRepository documentRepository,
                        FolderRepository folderRepository,
                        DocumentHistoryRepository historyRepository,
                        DocumentLabelRepository documentLabelRepository,
                        DocumentFavoriteRepository documentFavoriteRepository,
                        PermissionRepository permissionRepository,
                        WorkspaceMemberRepository workspaceMemberRepository,
                        WorkspaceContext workspaceContext,
                        AuditService auditService) {
        this.documentRepository = documentRepository;
        this.folderRepository = folderRepository;
        this.historyRepository = historyRepository;
        this.documentLabelRepository = documentLabelRepository;
        this.documentFavoriteRepository = documentFavoriteRepository;
        this.permissionRepository = permissionRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceContext = workspaceContext;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<TrashItemDto> list() {
        UUID workspaceId = workspaceContext.requireCurrentWorkspaceId();
        String me = workspaceContext.requireCurrentUserId().toString();
        boolean admin = isAdmin(workspaceId);

        List<FolderEntity> trashedFolders =
                folderRepository.findByWorkspaceIdAndUsableOrderByDeletedAtDesc(workspaceId, Integer.valueOf(0))
                        .stream()
                        .filter(f -> admin || me.equals(f.getDeletedBy()))
                        .toList();

        // 휴지통 폴더 ID 집합 — 부모가 이 안에 있으면 trashedParentId를 채운다.
        Set<String> trashedFolderIds = trashedFolders.stream()
                .map(FolderEntity::getFolderId)
                .collect(Collectors.toSet());

        List<TrashItemDto> items = new ArrayList<>();
        trashedFolders.stream()
                .map(f -> TrashItemDto.from(f, resolveParentInTrash(f.getParentId(), trashedFolderIds)))
                .forEach(items::add);

        documentRepository.findByWorkspaceIdAndUsableOrderByDeletedAtDesc(workspaceId, Integer.valueOf(0))
                .stream()
                .filter(d -> admin || me.equals(d.getDeletedBy()))
                .map(d -> TrashItemDto.from(d, resolveParentInTrash(d.getFolderId(), trashedFolderIds)))
                .forEach(items::add);

        items.sort(Comparator.comparing(TrashItemDto::deletedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return items;
    }

    /** 부모 ID가 휴지통 폴더 집합 안에 있으면 그 ID를, 아니면 null을 돌려준다. */
    private String resolveParentInTrash(String parentId, Set<String> trashedFolderIds) {
        if (parentId == null) return null;
        return trashedFolderIds.contains(parentId) ? parentId : null;
    }

    /**
     * 복원. 부모 폴더가 아직 휴지통에 있으면 targetFolderId 위치로, 없으면 루트로 되돌린다.
     *
     * <p>FOLDER 복원 시에는 하위 폴더/문서를 모두 함께 복원한다 (cascade).
     * 단, 직계 부모가 여전히 휴지통에 있는 하위 항목의 부모 참조는 이미 이 호출로 복원됐으므로
     * 트리 구조가 유지된다.
     *
     * @param targetFolderId 부모가 휴지통에 있을 때 복원될 위치. null이면 루트로 복원.
     */
    @Transactional
    public void restore(ResourceType resourceType, String resourceId, String targetFolderId) {
        UUID workspaceId = workspaceContext.requireCurrentWorkspaceId();
        String me = workspaceContext.requireCurrentUserId().toString();

        switch (resourceType) {
            case DOCUMENT -> restoreDocument(workspaceId, resourceId, me, targetFolderId);
            case FOLDER -> restoreFolder(workspaceId, resourceId, me, targetFolderId);
        }
        audit(workspaceId, AuditAction.RESTORE, resourceType, resourceId);
    }

    private void restoreDocument(UUID workspaceId, String resourceId, String me, String targetFolderId) {
        DocumentEntity doc = trashedDocument(workspaceId, resourceId);
        requireRestorer(workspaceId, doc.getDeletedBy(), me);
        if (doc.getFolderId() != null && isFolderTrashed(doc.getFolderId())) {
            // 부모가 아직 휴지통 — targetFolderId로 이동하거나 루트로 올린다.
            doc.moveTo(targetFolderId, doc.getOrdinal() == null ? 0 : doc.getOrdinal(), me);
        } else if (targetFolderId != null) {
            doc.moveTo(targetFolderId, doc.getOrdinal() == null ? 0 : doc.getOrdinal(), me);
        }
        doc.restore(me);
        documentRepository.save(doc);
    }

    private void restoreFolder(UUID workspaceId, String resourceId, String me, String targetFolderId) {
        FolderEntity folder = trashedFolder(workspaceId, resourceId);
        requireRestorer(workspaceId, folder.getDeletedBy(), me);

        boolean parentTrashed = folder.getParentId() != null && isFolderTrashed(folder.getParentId());
        if (parentTrashed || targetFolderId != null) {
            String dest = targetFolderId; // null이면 루트
            String parentPath = dest == null ? "/" : resolveActiveFolderPath(workspaceId, dest);
            folder.moveTo(dest, folder.getOrdinal() == null ? 0 : folder.getOrdinal(), me);
            folder.applyPath(FolderEntity.pathUnder(parentPath, folder.getFolderId()));
        }

        // 하위 폴더/문서 cascade 복원
        String subtreePrefix = folder.getPath(); // 자기 path로 자손을 찾는다.
        List<FolderEntity> descendants =
                folderRepository.findByPathStartingWithAndUsable(subtreePrefix, Integer.valueOf(0))
                        .stream()
                        .filter(f -> !f.getFolderId().equals(folder.getFolderId()))
                        .toList();

        List<String> allFolderIds = new ArrayList<>();
        allFolderIds.add(folder.getFolderId());
        descendants.stream().map(FolderEntity::getFolderId).forEach(allFolderIds::add);

        for (FolderEntity desc : descendants) {
            desc.restore(me);
        }
        folderRepository.saveAll(descendants);

        List<DocumentEntity> childDocs = documentRepository.findTrashedByFolderIds(allFolderIds);
        for (DocumentEntity doc : childDocs) {
            doc.restore(me);
        }
        documentRepository.saveAll(childDocs);

        folder.restore(me);
        folderRepository.save(folder);
    }

    /** 활성 폴더의 path를 가져온다. 존재하지 않거나 삭제됐으면 루트("/") 반환. */
    private String resolveActiveFolderPath(UUID workspaceId, String folderId) {
        return folderRepository.findByFolderIdAndUsable(folderId, Integer.valueOf(1))
                .filter(f -> f.getWorkspaceId().equals(workspaceId))
                .map(FolderEntity::getPath)
                .orElse("/");
    }

    /**
     * 영구 삭제. 문서는 본문·리비전·라벨·권한까지 함께 지운다 (FR-TRS-006).
     * 벡터 청크는 삭제 이벤트로 이미 제거된 상태다.
     */
    @Transactional
    public void purge(ResourceType resourceType, String resourceId) {
        UUID workspaceId = workspaceContext.requireCurrentWorkspaceId();
        String me = workspaceContext.requireCurrentUserId().toString();

        switch (resourceType) {
            case DOCUMENT -> {
                DocumentEntity doc = trashedDocument(workspaceId, resourceId);
                requireRestorer(workspaceId, doc.getDeletedBy(), me);
                historyRepository.deleteByDocumentId(resourceId);
                documentLabelRepository.deleteByDocumentId(resourceId);
                documentFavoriteRepository.deleteByDocumentId(resourceId);
                permissionRepository.deleteAll(
                        permissionRepository.findByResourceTypeAndResourceId(resourceType, resourceId));
                documentRepository.delete(doc);
            }
            case FOLDER -> {
                FolderEntity folder = trashedFolder(workspaceId, resourceId);
                requireRestorer(workspaceId, folder.getDeletedBy(), me);
                permissionRepository.deleteAll(
                        permissionRepository.findByResourceTypeAndResourceId(resourceType, resourceId));
                folderRepository.delete(folder);
            }
        }
        audit(workspaceId, AuditAction.DELETE, resourceType, resourceId);
    }

    private boolean isFolderTrashed(String folderId) {
        return folderRepository.findById(folderId).map(FolderEntity::isTrashed).orElse(true);
    }

    private DocumentEntity trashedDocument(UUID workspaceId, String documentId) {
        return documentRepository.findById(documentId)
                .filter(d -> d.getWorkspaceId().equals(workspaceId) && d.isTrashed())
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "휴지통에서 문서를 찾을 수 없습니다."));
    }

    private FolderEntity trashedFolder(UUID workspaceId, String folderId) {
        return folderRepository.findById(folderId)
                .filter(f -> f.getWorkspaceId().equals(workspaceId) && f.isTrashed())
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "휴지통에서 폴더를 찾을 수 없습니다."));
    }

    private void requireRestorer(UUID workspaceId, String deletedBy, String me) {
        if (me.equals(deletedBy) || isAdmin(workspaceId)) {
            return;
        }
        throw new BusinessException(Code.FORBIDDEN, "지운 사람이나 워크스페이스 관리자만 처리할 수 있습니다.");
    }

    private boolean isAdmin(UUID workspaceId) {
        return workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, workspaceContext.requireCurrentUserId())
                .map(WorkspaceMemberEntity::isAdmin)
                .orElse(false);
    }

    private void audit(UUID workspaceId, AuditAction action, ResourceType type, String id) {
        auditService.record(workspaceId, workspaceContext.requireCurrentUserId(), action,
                type, id, AuditResult.ALLOWED, isAdmin(workspaceId));
    }
}
