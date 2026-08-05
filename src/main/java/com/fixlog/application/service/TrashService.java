package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentLabelRepository;
import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.DocumentRevisionRepository;
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
import java.util.UUID;

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
    private final DocumentRevisionRepository revisionRepository;
    private final DocumentLabelRepository documentLabelRepository;
    private final PermissionRepository permissionRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceContext workspaceContext;
    private final AuditService auditService;

    public TrashService(DocumentRepository documentRepository,
                        FolderRepository folderRepository,
                        DocumentRevisionRepository revisionRepository,
                        DocumentLabelRepository documentLabelRepository,
                        PermissionRepository permissionRepository,
                        WorkspaceMemberRepository workspaceMemberRepository,
                        WorkspaceContext workspaceContext,
                        AuditService auditService) {
        this.documentRepository = documentRepository;
        this.folderRepository = folderRepository;
        this.revisionRepository = revisionRepository;
        this.documentLabelRepository = documentLabelRepository;
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

        List<TrashItemDto> items = new ArrayList<>();
        folderRepository.findByWorkspaceIdAndUsableOrderByDeletedAtDesc(workspaceId, Integer.valueOf(0))
                .stream()
                .filter(f -> admin || me.equals(f.getDeletedBy()))
                .map(TrashItemDto::from)
                .forEach(items::add);
        documentRepository.findByWorkspaceIdAndUsableOrderByDeletedAtDesc(workspaceId, Integer.valueOf(0))
                .stream()
                .filter(d -> admin || me.equals(d.getDeletedBy()))
                .map(TrashItemDto::from)
                .forEach(items::add);

        items.sort(Comparator.comparing(TrashItemDto::deletedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return items;
    }

    /**
     * 복원. 부모 폴더가 아직 휴지통에 있으면 루트로 되돌린다 (FR-TRS-005).
     * 삭제된 폴더 안으로 되살리면 트리에서 보이지 않는 고아가 된다.
     */
    @Transactional
    public void restore(ResourceType resourceType, String resourceId) {
        UUID workspaceId = workspaceContext.requireCurrentWorkspaceId();
        String me = workspaceContext.requireCurrentUserId().toString();

        switch (resourceType) {
            case DOCUMENT -> {
                DocumentEntity doc = trashedDocument(workspaceId, resourceId);
                requireRestorer(workspaceId, doc.getDeletedBy(), me);
                if (doc.getFolderId() != null && isFolderTrashed(doc.getFolderId())) {
                    doc.moveTo(null, doc.getOrdinal() == null ? 0 : doc.getOrdinal(), me);
                }
                doc.restore(me);
                documentRepository.save(doc);
            }
            case FOLDER -> {
                FolderEntity folder = trashedFolder(workspaceId, resourceId);
                requireRestorer(workspaceId, folder.getDeletedBy(), me);
                if (folder.getParentId() != null && isFolderTrashed(folder.getParentId())) {
                    folder.moveTo(null, folder.getOrdinal() == null ? 0 : folder.getOrdinal(), me);
                    folder.applyPath(FolderEntity.pathUnder("/", folder.getFolderId()));
                }
                folder.restore(me);
                folderRepository.save(folder);
            }
        }
        audit(workspaceId, AuditAction.RESTORE, resourceType, resourceId);
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
                revisionRepository.deleteAll(
                        revisionRepository.findByDocumentIdOrderByRevisionNoDesc(resourceId));
                documentLabelRepository.deleteByDocumentId(resourceId);
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
