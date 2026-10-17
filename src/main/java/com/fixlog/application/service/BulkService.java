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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 문서·폴더 혼합 일괄 작업 서비스.
 *
 * <p>bulkDelete / bulkMove는 의도적으로 @Transactional을 달지 않는다.
 * permissionEvaluator.require()가 @Transactional(readOnly=true)이므로,
 * 외부 트랜잭션 안에서 호출 후 BusinessException을 catch하면
 * Spring이 해당 트랜잭션을 rollback-only로 마킹해 UnexpectedRollbackException이 발생한다.
 *
 * <p>해결 방법: 두 단계로 분리.
 * Phase 1 — 권한 검증(트랜잭션 없음): permissionEvaluator 호출이 각자 독립 read-only 트랜잭션에서 실행.
 * Phase 2 — 실제 저장(TransactionTemplate): 검증된 항목만 하나의 쓰기 트랜잭션으로 처리.
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
    private final TransactionTemplate transactionTemplate;

    public BulkService(DocumentRepository documentRepository,
                       FolderRepository folderRepository,
                       DocumentService documentService,
                       FolderService folderService,
                       PermissionEvaluator permissionEvaluator,
                       DocumentPdfGenerator pdfGenerator,
                       ApplicationEventPublisher eventPublisher,
                       PlatformTransactionManager transactionManager) {
        this.documentRepository = documentRepository;
        this.folderRepository = folderRepository;
        this.documentService = documentService;
        this.folderService = folderService;
        this.permissionEvaluator = permissionEvaluator;
        this.pdfGenerator = pdfGenerator;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // @Transactional 없음 — 이유는 클래스 JavaDoc 참조
    public BulkOperationResultDto bulkDelete(List<String> documentIds, List<String> folderIds) {
        if (safe(documentIds).isEmpty() && safe(folderIds).isEmpty()) {
            throw new BusinessException(Code.INVALID_REQUEST, "삭제할 문서 또는 폴더가 없습니다.");
        }

        String userId = requireUserId();
        List<String> succeededIds = new ArrayList<>();
        List<BulkOperationResultDto.FailedItem> failures = new ArrayList<>();
        int total = safe(documentIds).size() + safe(folderIds).size();

        // Phase 1: 권한 검증 — 실패해도 외부 트랜잭션이 없으므로 rollback-only 오염 없음
        List<String> validDocIds = new ArrayList<>();
        for (String docId : safe(documentIds)) {
            try {
                permissionEvaluator.require(ResourceType.DOCUMENT, docId, PermissionAction.DELETE);
                validDocIds.add(docId);
            } catch (BusinessException e) {
                failures.add(new BulkOperationResultDto.FailedItem(docId, e.getMessage()));
            }
        }

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

        // 조상이 같은 요청에 포함된 경우 자손은 건너뜀 (엔티티의 path 필드는 detached 후에도 접근 가능)
        List<String> folderRootIds = folderCandidates.stream()
                .filter(f -> {
                    List<String> segs = f.pathSegments();
                    return segs.subList(0, Math.max(0, segs.size() - 1)).stream()
                            .noneMatch(folderCandidateIds::contains);
                })
                .map(FolderEntity::getFolderId)
                .toList();

        // Phase 2: 실제 삭제를 하나의 쓰기 트랜잭션에서 처리
        transactionTemplate.execute(status -> {
            List<DocumentEntity> docs = validDocIds.stream()
                    .map(id -> documentRepository.findByDocumentIdAndUsable(id, Integer.valueOf(1)).orElse(null))
                    .filter(d -> d != null)
                    .toList();
            for (DocumentEntity doc : docs) {
                doc.softDelete(userId);
            }
            documentRepository.saveAll(docs);
            for (DocumentEntity doc : docs) {
                eventPublisher.publishEvent(new DocumentDeletedEvent(doc.getDocumentId()));
                succeededIds.add(doc.getDocumentId());
            }

            for (String folderId : folderRootIds) {
                FolderEntity root = folderRepository
                        .findByFolderIdAndUsable(folderId, Integer.valueOf(1)).orElse(null);
                if (root != null) {
                    folderService.deleteSubtree(root, userId);
                    succeededIds.add(folderId);
                }
            }
            return null;
        });

        return BulkOperationResultDto.of(succeededIds, failures, total);
    }

    // @Transactional 없음 — 이유는 클래스 JavaDoc 참조
    public BulkOperationResultDto bulkMove(List<String> documentIds, List<String> folderIds, String targetFolderId) {
        if (safe(documentIds).isEmpty() && safe(folderIds).isEmpty()) {
            throw new BusinessException(Code.INVALID_REQUEST, "이동할 문서 또는 폴더가 없습니다.");
        }

        String userId = requireUserId();
        List<String> succeededIds = new ArrayList<>();
        List<BulkOperationResultDto.FailedItem> failures = new ArrayList<>();
        int total = safe(documentIds).size() + safe(folderIds).size();

        // Phase 1: 대상 폴더 검증 및 항목별 권한/순환 이동 검증
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

        final UUID finalTargetWorkspaceId = targetWorkspaceId;
        final String finalTargetParentPath = targetParentPath;

        List<String> validDocIds = new ArrayList<>();
        Map<String, UUID> docWorkspaceMap = new LinkedHashMap<>();
        for (String docId : safe(documentIds)) {
            try {
                permissionEvaluator.require(ResourceType.DOCUMENT, docId, PermissionAction.EDIT);
                DocumentEntity doc = documentService.load(docId);
                if (targetFolderId != null && !doc.getWorkspaceId().equals(finalTargetWorkspaceId)) {
                    failures.add(new BulkOperationResultDto.FailedItem(docId, "대상 폴더가 같은 워크스페이스에 없습니다."));
                    continue;
                }
                validDocIds.add(docId);
                docWorkspaceMap.put(docId, doc.getWorkspaceId());
            } catch (BusinessException e) {
                failures.add(new BulkOperationResultDto.FailedItem(docId, e.getMessage()));
            }
        }

        List<String> validFolderIds = new ArrayList<>();
        Map<String, UUID> folderWorkspaceMap = new LinkedHashMap<>();
        for (String folderId : safe(folderIds)) {
            try {
                if (folderId.equals(targetFolderId)) {
                    failures.add(new BulkOperationResultDto.FailedItem(folderId, "폴더를 자기 자신으로 이동할 수 없습니다."));
                    continue;
                }
                FolderEntity folder = folderService.loadPermitted(folderId, PermissionAction.EDIT);
                UUID workspaceId = folder.getWorkspaceId();
                if (targetFolderId != null) {
                    if (!workspaceId.equals(finalTargetWorkspaceId)) {
                        failures.add(new BulkOperationResultDto.FailedItem(folderId, "대상 폴더가 같은 워크스페이스에 없습니다."));
                        continue;
                    }
                    if (folderService.isDescendantOf(folderId, targetFolderId, workspaceId)) {
                        failures.add(new BulkOperationResultDto.FailedItem(folderId, "폴더를 자신의 하위 폴더로 이동할 수 없습니다."));
                        continue;
                    }
                }
                validFolderIds.add(folderId);
                folderWorkspaceMap.put(folderId, workspaceId);
            } catch (BusinessException e) {
                failures.add(new BulkOperationResultDto.FailedItem(folderId, e.getMessage()));
            }
        }

        // Phase 2: 실제 이동을 하나의 쓰기 트랜잭션에서 처리
        transactionTemplate.execute(status -> {
            for (String docId : validDocIds) {
                DocumentEntity doc = documentRepository
                        .findByDocumentIdAndUsable(docId, Integer.valueOf(1)).orElse(null);
                if (doc == null) continue;
                UUID ws = docWorkspaceMap.getOrDefault(docId, doc.getWorkspaceId());
                int ordinal = documentRepository.maxOrdinal(targetFolderId, ws) + 1;
                doc.moveTo(targetFolderId, ordinal, userId);
                documentRepository.save(doc);
                succeededIds.add(docId);
            }

            for (String folderId : validFolderIds) {
                FolderEntity folder = folderRepository
                        .findByFolderIdAndUsable(folderId, Integer.valueOf(1)).orElse(null);
                if (folder == null) continue;
                UUID ws = folderWorkspaceMap.getOrDefault(folderId, folder.getWorkspaceId());
                int ordinal = folderService.nextOrdinal(targetFolderId, ws);
                folder.moveTo(targetFolderId, ordinal, userId);
                folderService.repath(folder, finalTargetParentPath);
                folderRepository.save(folder);
                succeededIds.add(folderId);
            }
            return null;
        });

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

            for (String docId : safe(documentIds)) {
                DocumentEntity doc = documentService.loadForDownload(docId);
                byte[] pdf = pdfGenerator.generate(doc, documentService.watermarkFor(doc));
                zos.putNextEntry(new ZipEntry(dedup(doc.getTitle(), rootNames) + ".pdf"));
                zos.write(pdf);
                zos.closeEntry();
            }

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
