package com.fixlog.application.service;

import com.fixlog.application.event.DocumentDeletedEvent;
import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.security.SecurityUtil;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.domain.model.PermissionAction;
import com.fixlog.domain.model.ResourceType;
import com.fixlog.presentation.dto.response.BulkOperationResultDto;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 문서·폴더 혼합 일괄 작업 서비스.
 *
 * <p>UI에서 같은 레벨(같은 부모 폴더)에 있는 문서와 폴더를 함께 선택해
 * 삭제·이동·다운로드할 때 사용한다. 개별 권한 확인은 기존 서비스의
 * package-private 헬퍼를 재사용하고, 트랜잭션은 이 서비스가 직접 관리한다.
 */
@Service
public class BulkService {

    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final DocumentService documentService;
    private final FolderService folderService;
    private final PermissionEvaluator permissionEvaluator;
    private final DocumentPdfGenerator pdfGenerator;
    private final ApplicationEventPublisher eventPublisher;

    public BulkService(DocumentRepository documentRepository,
                       FolderRepository folderRepository,
                       DocumentService documentService,
                       FolderService folderService,
                       PermissionEvaluator permissionEvaluator,
                       DocumentPdfGenerator pdfGenerator,
                       ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.folderRepository = folderRepository;
        this.documentService = documentService;
        this.folderService = folderService;
        this.permissionEvaluator = permissionEvaluator;
        this.pdfGenerator = pdfGenerator;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public BulkOperationResultDto bulkDelete(List<String> documentIds, List<String> folderIds) {
        if (safe(documentIds).isEmpty() && safe(folderIds).isEmpty()) {
            throw new BusinessException(Code.INVALID_REQUEST, "삭제할 문서 또는 폴더가 없습니다.");
        }

        String userId = requireUserId();
        List<String> succeededIds = new ArrayList<>();
        List<BulkOperationResultDto.FailedItem> failures = new ArrayList<>();
        int total = safe(documentIds).size() + safe(folderIds).size();

        // 문서 삭제
        List<DocumentEntity> docsToDelete = new ArrayList<>();
        List<String> deletedDocIds = new ArrayList<>();
        for (String docId : safe(documentIds)) {
            try {
                permissionEvaluator.require(ResourceType.DOCUMENT, docId, PermissionAction.DELETE);
                docsToDelete.add(documentService.load(docId));
                deletedDocIds.add(docId);
                succeededIds.add(docId);
            } catch (BusinessException e) {
                failures.add(new BulkOperationResultDto.FailedItem(docId, e.getMessage()));
            }
        }
        for (DocumentEntity doc : docsToDelete) {
            doc.softDelete(userId);
        }
        documentRepository.saveAll(docsToDelete);
        for (String docId : deletedDocIds) {
            eventPublisher.publishEvent(new DocumentDeletedEvent(docId));
        }

        // 폴더 삭제 — 조상이 같은 요청에 포함된 경우 자손은 건너뜀
        List<FolderEntity> folderCandidates = new ArrayList<>();
        Set<String> folderCandidateIds = new LinkedHashSet<>();
        for (String folderId : safe(folderIds)) {
            try {
                FolderEntity folder = folderService.loadPermitted(folderId, PermissionAction.DELETE);
                folderCandidates.add(folder);
                folderCandidateIds.add(folderId);
            } catch (BusinessException e) {
                failures.add(new BulkOperationResultDto.FailedItem(folderId, e.getMessage()));
            }
        }
        List<FolderEntity> folderRoots = folderCandidates.stream()
                .filter(f -> {
                    List<String> segs = f.pathSegments();
                    return segs.subList(0, Math.max(0, segs.size() - 1)).stream()
                            .noneMatch(folderCandidateIds::contains);
                })
                .toList();
        for (FolderEntity root : folderRoots) {
            folderService.deleteSubtree(root, userId);
            succeededIds.add(root.getFolderId());
        }

        return BulkOperationResultDto.of(succeededIds, failures, total);
    }

    @Transactional
    public BulkOperationResultDto bulkMove(List<String> documentIds, List<String> folderIds, String targetFolderId) {
        if (safe(documentIds).isEmpty() && safe(folderIds).isEmpty()) {
            throw new BusinessException(Code.INVALID_REQUEST, "이동할 문서 또는 폴더가 없습니다.");
        }

        String userId = requireUserId();
        List<String> succeededIds = new ArrayList<>();
        List<BulkOperationResultDto.FailedItem> failures = new ArrayList<>();
        int total = safe(documentIds).size() + safe(folderIds).size();

        // 대상 폴더 검증
        UUID targetWorkspaceId = null;
        String targetParentPath = "/";
        if (targetFolderId != null) {
            permissionEvaluator.require(ResourceType.FOLDER, targetFolderId, PermissionAction.EDIT);
            FolderEntity targetFolder = folderRepository
                    .findByFolderIdAndUsable(targetFolderId, Integer.valueOf(1))
                    .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "대상 폴더를 찾을 수 없습니다."));
            targetWorkspaceId = targetFolder.getWorkspaceId();
            targetParentPath = targetFolder.getPath();
        }

        // 문서 이동
        List<DocumentEntity> docsToMove = new ArrayList<>();
        for (String docId : safe(documentIds)) {
            try {
                permissionEvaluator.require(ResourceType.DOCUMENT, docId, PermissionAction.EDIT);
                DocumentEntity doc = documentService.load(docId);
                if (targetFolderId != null && !doc.getWorkspaceId().equals(targetWorkspaceId)) {
                    failures.add(new BulkOperationResultDto.FailedItem(docId, "대상 폴더가 같은 워크스페이스에 없습니다."));
                    continue;
                }
                docsToMove.add(doc);
                succeededIds.add(docId);
            } catch (BusinessException e) {
                failures.add(new BulkOperationResultDto.FailedItem(docId, e.getMessage()));
            }
        }
        for (DocumentEntity doc : docsToMove) {
            int ordinal = documentRepository.maxOrdinal(targetFolderId, doc.getWorkspaceId()) + 1;
            doc.moveTo(targetFolderId, ordinal, userId);
        }
        documentRepository.saveAll(docsToMove);

        // 폴더 이동
        for (String folderId : safe(folderIds)) {
            try {
                if (folderId.equals(targetFolderId)) {
                    failures.add(new BulkOperationResultDto.FailedItem(folderId, "폴더를 자기 자신으로 이동할 수 없습니다."));
                    continue;
                }
                FolderEntity folder = folderService.loadPermitted(folderId, PermissionAction.EDIT);
                UUID workspaceId = folder.getWorkspaceId();
                if (targetFolderId != null) {
                    if (targetWorkspaceId != null && !workspaceId.equals(targetWorkspaceId)) {
                        failures.add(new BulkOperationResultDto.FailedItem(folderId, "대상 폴더가 같은 워크스페이스에 없습니다."));
                        continue;
                    }
                    if (folderService.isDescendantOf(folderId, targetFolderId, workspaceId)) {
                        failures.add(new BulkOperationResultDto.FailedItem(folderId, "폴더를 자신의 하위 폴더로 이동할 수 없습니다."));
                        continue;
                    }
                }
                int ordinal = folderService.nextOrdinal(targetFolderId, workspaceId);
                folder.moveTo(targetFolderId, ordinal, userId);
                folderService.repath(folder, targetParentPath);
                folderRepository.save(folder);
                succeededIds.add(folderId);
            } catch (BusinessException e) {
                failures.add(new BulkOperationResultDto.FailedItem(folderId, e.getMessage()));
            }
        }

        return BulkOperationResultDto.of(succeededIds, failures, total);
    }

    @Transactional(readOnly = true)
    public DocumentService.BulkDownloadResult bulkDownload(List<String> documentIds, List<String> folderIds) {
        if (safe(documentIds).isEmpty() && safe(folderIds).isEmpty()) {
            throw new BusinessException(Code.INVALID_REQUEST, "다운로드할 문서 또는 폴더가 없습니다.");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            Set<String> rootNames = new HashSet<>();

            // 직접 선택된 문서
            for (String docId : safe(documentIds)) {
                DocumentEntity doc = documentService.loadForDownload(docId);
                byte[] pdf = pdfGenerator.generate(doc, documentService.watermarkFor(doc));
                zos.putNextEntry(new ZipEntry(dedup(doc.getTitle(), rootNames) + ".pdf"));
                zos.write(pdf);
                zos.closeEntry();
            }

            // 선택된 폴더 — 하위 문서를 폴더명/문서명.pdf 구조로 포함
            for (String folderId : safe(folderIds)) {
                permissionEvaluator.require(ResourceType.FOLDER, folderId, PermissionAction.VIEW);
                FolderEntity folder = folderRepository
                        .findByFolderIdAndUsable(folderId, Integer.valueOf(1))
                        .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));
                String dirName = dedup(folder.getFolderName(), rootNames) + "/";
                addFolderContentsToZip(folder, dirName, zos);
            }
        } catch (IOException e) {
            throw new BusinessException(Code.UNKNOWN, "ZIP 파일 생성에 실패했습니다.");
        }

        return new DocumentService.BulkDownloadResult(baos.toByteArray());
    }

    /** 폴더 안의 문서와 하위 폴더를 재귀적으로 ZIP에 추가한다. */
    private void addFolderContentsToZip(FolderEntity folder, String basePath, ZipOutputStream zos) throws IOException {
        UUID workspaceId = folder.getWorkspaceId();
        Set<String> usedNames = new HashSet<>();

        List<DocumentEntity> docs = documentRepository
                .findByWorkspaceIdAndFolderIdAndUsableOrderByOrdinalAscCreateTimeAsc(
                        workspaceId, folder.getFolderId(), Integer.valueOf(1));
        for (DocumentEntity doc : docs) {
            permissionEvaluator.requireDownload(ResourceType.DOCUMENT, doc.getDocumentId());
            byte[] pdf = pdfGenerator.generate(doc, documentService.watermarkFor(doc));
            zos.putNextEntry(new ZipEntry(basePath + dedup(doc.getTitle(), usedNames) + ".pdf"));
            zos.write(pdf);
            zos.closeEntry();
        }

        List<FolderEntity> subFolders = folderRepository
                .findByWorkspaceIdAndParentIdAndUsableOrderByOrdinalAscCreateTimeAsc(
                        workspaceId, folder.getFolderId(), Integer.valueOf(1));
        for (FolderEntity sub : subFolders) {
            addFolderContentsToZip(sub, basePath + safeName(sub.getFolderName()) + "/", zos);
        }
    }

    private String dedup(String name, Set<String> usedNames) {
        String safe = (name == null || name.isBlank()) ? "항목" : name.replaceAll("[/\\\\:*?\"<>|]", "_");
        if (usedNames.add(safe)) return safe;
        int i = 1;
        while (!usedNames.add(safe + " (" + i + ")")) i++;
        return safe + " (" + i + ")";
    }

    private String safeName(String name) {
        return (name == null || name.isBlank()) ? "폴더" : name.replaceAll("[/\\\\:*?\"<>|]", "_");
    }

    private List<String> safe(List<String> list) {
        return list == null ? List.of() : list;
    }

    private String requireUserId() {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(Code.UNAUTHORIZED, "인증 정보가 없습니다.");
        }
        return userId;
    }
}
