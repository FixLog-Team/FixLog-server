package com.fixlog.application.service;

import tools.jackson.databind.JsonNode;
import com.fixlog.application.event.DocumentDeletedEvent;
import com.fixlog.application.event.DocumentSavedEvent;
import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.security.SecurityUtil;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.DocumentHistoryEntity;
import com.fixlog.domain.model.DocumentHistorySource;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.presentation.dto.request.DocumentCreateRequest;
import com.fixlog.presentation.dto.request.DocumentMoveRequest;
import com.fixlog.presentation.dto.request.DocumentSaveRequest;
import com.fixlog.presentation.dto.request.DocumentTitleRequest;
import com.fixlog.presentation.dto.response.DocumentSaveStateDto;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Integer ACTIVE = 1;

    /** 목록 기본 정렬. 최근 수정한 문서가 먼저 온다. */
    private static final Comparator<DocumentEntity> RECENTLY_UPDATED =
            Comparator.comparing(DocumentEntity::getUpdateTime,
                    Comparator.nullsLast(Comparator.reverseOrder()));

    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final DocumentTextExtractor textExtractor;
    private final DocumentPdfGenerator pdfGenerator;
    private final DocumentHistoryService historyService;
    private final PermissionResolver permissionResolver;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentService(DocumentRepository documentRepository,
                           FolderRepository folderRepository,
                           DocumentTextExtractor textExtractor,
                           DocumentPdfGenerator pdfGenerator,
                           DocumentHistoryService historyService,
                           PermissionResolver permissionResolver,
                           ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.folderRepository = folderRepository;
        this.textExtractor = textExtractor;
        this.pdfGenerator = pdfGenerator;
        this.historyService = historyService;
        this.permissionResolver = permissionResolver;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public DocumentEntity create(String workspaceId, DocumentCreateRequest req) {
        String userId = SecurityUtil.requireCurrentUserId();
        String targetWorkspaceId;

        if (req.folderId() != null) {
            // 문서를 넣을 폴더에 접근할 수 있어야 한다. 워크스페이스도 그 폴더에서 따라간다.
            FolderEntity folder = requireAccessibleFolder(req.folderId(), userId);
            targetWorkspaceId = folder.getWorkspaceId();
        } else {
            targetWorkspaceId = permissionResolver.resolveWorkspaceId(userId, workspaceId);
        }

        int ordinal = documentRepository.maxOrdinal(req.folderId(), targetWorkspaceId) + 1;
        DocumentEntity doc = new DocumentEntity(
                UUID.randomUUID().toString(),
                targetWorkspaceId,
                req.folderId(),
                req.title() != null && !req.title().isBlank() ? req.title() : "제목 없음",
                "[]",
                "",
                hashContent("[]"),
                ordinal,
                userId
        );
        return documentRepository.save(doc);
    }

    /**
     * 문서 목록. 접근 가능한 문서만 담기며, 총 개수도 판정 이후 값으로 계산한다.
     *
     * <p>DB 페이지네이션 뒤에 권한 필터를 걸면 "총 152건" 같은 숫자에 접근 불가 문서가
     * 섞여 존재가 새어 나간다. 그래서 판정을 먼저 하고 그 결과를 페이지로 자른다.
     */
    @Transactional(readOnly = true)
    public Page<DocumentEntity> list(String workspaceId, String folderId, Pageable pageable) {
        String userId = SecurityUtil.requireCurrentUserId();
        String resolvedWorkspaceId = permissionResolver.resolveWorkspaceId(userId, workspaceId);
        PermissionSnapshot snapshot = permissionResolver.snapshot(resolvedWorkspaceId, userId);

        List<DocumentEntity> accessible = documentRepository
                .findByWorkspaceIdAndUsable(resolvedWorkspaceId, ACTIVE).stream()
                .filter(document -> folderId == null || folderId.equals(document.getFolderId()))
                .filter(snapshot::isAllowed)
                .sorted(RECENTLY_UPDATED)
                .toList();

        int from = (int) Math.min(pageable.getOffset(), accessible.size());
        int to = Math.min(from + pageable.getPageSize(), accessible.size());
        return new PageImpl<>(accessible.subList(from, to), pageable, accessible.size());
    }

    @Transactional(readOnly = true)
    public DocumentEntity getDocument(String documentId) {
        return loadAccessible(documentId);
    }

    @Transactional
    public DocumentEntity saveContent(String documentId, DocumentSaveRequest req) {
        DocumentEntity doc = loadAccessible(documentId);
        textExtractor.validateBlocks(req.blocks());
        String canonicalJson = normalizeBlocks(req.blocks());
        String plainText = textExtractor.extract(canonicalJson);
        String hash = hashContent(canonicalJson);
        boolean contentChanged = !hash.equals(doc.getContentHash());
        boolean titleChanged = !Objects.equals(req.title(), doc.getTitle());
        // 덮어쓰기 전에 직전 내용을 히스토리로 밀어넣는다. 내용이 그대로면 버전을 만들지 않는다.
        if (contentChanged) {
            historyService.archive(doc, DocumentHistorySource.MANUAL);
        }
        doc.updateContent(req.title(), canonicalJson, plainText, hash, SecurityUtil.requireCurrentUserId());
        DocumentEntity saved = documentRepository.save(doc);
        eventPublisher.publishEvent(new DocumentSavedEvent(saved, contentChanged, titleChanged));
        return saved;
    }

    /**
     * 과거 버전으로 문서를 되돌린다.
     * 복원 직전 내용도 히스토리에 남기므로 복원 자체를 다시 되돌릴 수 있다.
     */
    @Transactional
    public DocumentEntity restoreFromHistory(String documentId, String historyId) {
        DocumentEntity doc = loadAccessible(documentId);
        DocumentHistoryEntity version = historyService.getVersion(documentId, historyId);

        boolean contentChanged = !Objects.equals(version.getContentHash(), doc.getContentHash());
        boolean titleChanged = !Objects.equals(version.getTitle(), doc.getTitle());
        if (!contentChanged && !titleChanged) {
            return doc;
        }

        if (contentChanged) {
            historyService.archive(doc, DocumentHistorySource.RESTORE);
        }
        String plainText = textExtractor.extract(version.getBlocks());
        doc.updateContent(version.getTitle(), version.getBlocks(), plainText, version.getContentHash(),
                SecurityUtil.requireCurrentUserId());
        DocumentEntity saved = documentRepository.save(doc);
        // 본문이 과거 내용으로 바뀌었으므로 벡터 인덱스도 다시 만들어져야 한다.
        eventPublisher.publishEvent(new DocumentSavedEvent(saved, contentChanged, titleChanged));
        return saved;
    }

    @Transactional
    public DocumentEntity updateTitle(String documentId, DocumentTitleRequest req) {
        DocumentEntity doc = loadAccessible(documentId);
        doc.updateTitle(req.title(), SecurityUtil.requireCurrentUserId());
        return documentRepository.save(doc);
    }

    @Transactional(readOnly = true)
    public DocumentSaveStateDto getSaveState(String documentId) {
        return DocumentSaveStateDto.from(loadAccessible(documentId));
    }

    @Transactional
    public DocumentEntity duplicate(String documentId) {
        DocumentEntity original = loadAccessible(documentId);
        String userId = SecurityUtil.requireCurrentUserId();
        int ordinal = documentRepository.maxOrdinal(original.getFolderId(), original.getWorkspaceId()) + 1;
        DocumentEntity copy = new DocumentEntity(
                UUID.randomUUID().toString(),
                original.getWorkspaceId(),
                original.getFolderId(),
                original.getTitle() + " (1)",
                original.getBlocks(),
                original.getPlainText(),
                original.getContentHash(),
                ordinal,
                userId
        );
        return documentRepository.save(copy);
    }

    @Transactional
    public void delete(String documentId) {
        DocumentEntity doc = loadAccessible(documentId);
        doc.softDelete(SecurityUtil.requireCurrentUserId());
        documentRepository.save(doc);
        eventPublisher.publishEvent(new DocumentDeletedEvent(documentId));
    }

    @Transactional
    public List<DocumentEntity> reorder(String folderId, List<String> documentIds) {
        List<DocumentEntity> result = new ArrayList<>();
        for (int i = 0; i < documentIds.size(); i++) {
            DocumentEntity doc = loadAccessible(documentIds.get(i));
            if (!Objects.equals(folderId, doc.getFolderId())) {
                throw new BusinessException(Code.INVALID_REQUEST,
                        "해당 폴더에 속하지 않은 문서가 포함되어 있습니다.");
            }
            doc.applyOrdinal(i);
            result.add(documentRepository.save(doc));
        }
        return result;
    }

    @Transactional
    public DocumentEntity move(String documentId, DocumentMoveRequest req) {
        String userId = SecurityUtil.requireCurrentUserId();
        DocumentEntity doc = loadAccessible(documentId);

        String targetFolderId = req.folderId();
        if (targetFolderId != null) {
            FolderEntity target = requireAccessibleFolder(targetFolderId, userId);
            if (!target.getWorkspaceId().equals(doc.getWorkspaceId())) {
                throw new BusinessException(Code.INVALID_REQUEST, "다른 워크스페이스로는 이동할 수 없습니다.");
            }
        }

        int ordinal = documentRepository.maxOrdinal(targetFolderId, doc.getWorkspaceId()) + 1;
        doc.moveTo(targetFolderId, ordinal, userId);
        return documentRepository.save(doc);
    }

    @Transactional(readOnly = true)
    public byte[] downloadPdf(String documentId) {
        DocumentEntity doc = loadAccessible(documentId);
        return pdfGenerator.generate(doc);
    }

    public record PdfResult(String title, byte[] bytes) {}

    @Transactional(readOnly = true)
    public PdfResult downloadPdfResult(String documentId) {
        DocumentEntity doc = loadAccessible(documentId);
        return new PdfResult(doc.getTitle(), pdfGenerator.generate(doc));
    }

    /**
     * 문서 단건 접근의 유일한 관문. 모든 단건 조회 · 수정 · 삭제가 이 메서드를 지난다.
     * 접근 불가 문서는 존재 자체를 알리지 않기 위해 404로 응답한다.
     */
    private DocumentEntity loadAccessible(String documentId) {
        String userId = SecurityUtil.requireCurrentUserId();
        DocumentEntity document = documentRepository.findByDocumentIdAndUsable(documentId, ACTIVE)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "문서를 찾을 수 없습니다."));
        if (!permissionResolver.snapshot(document.getWorkspaceId(), userId).isAllowed(document)) {
            throw new BusinessException(Code.NOT_FOUND, "문서를 찾을 수 없습니다.");
        }
        return document;
    }

    private FolderEntity requireAccessibleFolder(String folderId, String userId) {
        FolderEntity folder = folderRepository.findByFolderIdAndUsable(folderId, ACTIVE)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));
        if (!permissionResolver.snapshot(folder.getWorkspaceId(), userId).isAllowed(folder)) {
            throw new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다.");
        }
        return folder;
    }

    private String normalizeBlocks(JsonNode blocks) {
        if (blocks == null || blocks.isNull()) return "[]";
        return blocks.toString();
    }

    private String hashContent(String canonicalJson) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
