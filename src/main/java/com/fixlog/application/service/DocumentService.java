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
import com.fixlog.domain.model.PermissionAction;
import com.fixlog.domain.model.ResourceType;
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
import java.util.List;
import java.util.UUID;

/**
 * 문서 서비스.
 *
 * <p>접근 여부는 이 클래스가 판단하지 않는다. 어떤 행위인지만 정하고
 * {@link PermissionEvaluator}에 묻는다. {@code create_user}는 작성자 정보로만 남으며
 * 접근 제어에 쓰이지 않는다 (FR-PRM-011).
 */
@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final DocumentTextExtractor textExtractor;
    private final DocumentPdfGenerator pdfGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final WorkspaceContext workspaceContext;
    private final PermissionEvaluator permissionEvaluator;
    private final PermissionService permissionService;

    public DocumentService(DocumentRepository documentRepository,
                           FolderRepository folderRepository,
                           DocumentTextExtractor textExtractor,
                           DocumentPdfGenerator pdfGenerator,
                           ApplicationEventPublisher eventPublisher,
                           WorkspaceContext workspaceContext,
                           PermissionEvaluator permissionEvaluator,
                           PermissionService permissionService) {
        this.documentRepository = documentRepository;
        this.folderRepository = folderRepository;
        this.textExtractor = textExtractor;
        this.pdfGenerator = pdfGenerator;
        this.eventPublisher = eventPublisher;
        this.workspaceContext = workspaceContext;
        this.permissionEvaluator = permissionEvaluator;
        this.permissionService = permissionService;
    }

    @Transactional
    public DocumentEntity create(DocumentCreateRequest req) {
        String userId = requireUserId();
        UUID workspaceId = workspaceContext.requireCurrentWorkspaceId();

        // 폴더 안에 만들려면 그 폴더를 편집할 수 있어야 한다. 루트는 워크스페이스 구성원이면 된다.
        if (req.folderId() != null) {
            permissionEvaluator.require(ResourceType.FOLDER, req.folderId(), PermissionAction.EDIT);
        }

        int ordinal = documentRepository.maxOrdinal(req.folderId(), workspaceId) + 1;
        DocumentEntity doc = new DocumentEntity(
                UUID.randomUUID().toString(),
                workspaceId,
                req.folderId(),
                req.title() != null && !req.title().isBlank() ? req.title() : "제목 없음",
                "[]",
                "",
                hashContent("[]"),
                ordinal,
                userId
        );
        return saveWithOwnership(doc, userId);
    }

    /**
     * 현재 워크스페이스에서 볼 수 있는 문서만 돌려준다.
     *
     * <p>권한 조건을 쿼리에 넣지 않기로 했으므로 DB에서 페이지를 자를 수 없다. 워크스페이스
     * 문서를 가져와 판정으로 거른 뒤 페이지를 만든다. 워크스페이스 규모가 커지면 이 방식이
     * 부담이 되므로, 공유 목록을 설계하는 시점에 다시 본다 (A-03).
     */
    @Transactional(readOnly = true)
    public Page<DocumentEntity> list(String folderId, Pageable pageable) {
        UUID workspaceId = workspaceContext.requireCurrentWorkspaceId();
        PermissionEvaluator.Scope scope = permissionEvaluator.scopeFor(workspaceId);

        List<DocumentEntity> candidates = folderId != null
                ? documentRepository.findByWorkspaceIdAndFolderIdAndUsableOrderByOrdinalAscCreateTimeAsc(
                        workspaceId, folderId, Integer.valueOf(1))
                : documentRepository.findByWorkspaceIdAndUsableOrderByOrdinalAscCreateTimeAsc(
                        workspaceId, Integer.valueOf(1));

        List<DocumentEntity> visible = candidates.stream()
                .filter(doc -> scope.canViewDocument(doc.getDocumentId(), doc.getFolderId()))
                .toList();

        return paginate(visible, pageable);
    }

    /**
     * 내가 만들지 않았지만 권한을 받은 문서 (FR-SHR-005).
     *
     * <p>관리자 특권은 여기서 세지 않는다. 관리자가 워크스페이스의 모든 문서를 "공유받았다"고
     * 보면 목록이 의미를 잃기 때문이다.
     */
    @Transactional(readOnly = true)
    public List<DocumentEntity> sharedWithMe() {
        UUID workspaceId = workspaceContext.requireCurrentWorkspaceId();
        String me = requireUserId();
        PermissionEvaluator.Scope scope = permissionEvaluator.explicitScopeFor(workspaceId);

        return documentRepository
                .findByWorkspaceIdAndUsableOrderByOrdinalAscCreateTimeAsc(workspaceId, Integer.valueOf(1))
                .stream()
                .filter(doc -> !me.equals(doc.getCreateUser()))
                .filter(doc -> scope.canViewDocument(doc.getDocumentId(), doc.getFolderId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentEntity getDocument(String documentId) {
        return loadPermitted(documentId, PermissionAction.VIEW);
    }

    @Transactional
    public DocumentEntity saveContent(String documentId, DocumentSaveRequest req) {
        DocumentEntity doc = loadPermitted(documentId, PermissionAction.EDIT);
        textExtractor.validateBlocks(req.blocks());
        String canonicalJson = normalizeBlocks(req.blocks());
        String plainText = textExtractor.extract(canonicalJson);
        String hash = hashContent(canonicalJson);
        doc.updateContent(req.title(), canonicalJson, plainText, hash, requireUserId());
        DocumentEntity saved = documentRepository.save(doc);
        eventPublisher.publishEvent(new DocumentSavedEvent(saved));
        return saved;
    }

    @Transactional
    public DocumentEntity updateTitle(String documentId, DocumentTitleRequest req) {
        DocumentEntity doc = loadPermitted(documentId, PermissionAction.EDIT);
        doc.updateTitle(req.title(), requireUserId());
        return documentRepository.save(doc);
    }

    @Transactional(readOnly = true)
    public DocumentSaveStateDto getSaveState(String documentId) {
        return DocumentSaveStateDto.from(loadPermitted(documentId, PermissionAction.VIEW));
    }

    /** 복제는 같은 자리에 새 문서를 만드는 일이라 편집 권한을 요구한다. */
    @Transactional
    public DocumentEntity duplicate(String documentId) {
        DocumentEntity original = loadPermitted(documentId, PermissionAction.EDIT);
        String userId = requireUserId();
        int ordinal = documentRepository.maxOrdinal(original.getFolderId(), original.getWorkspaceId()) + 1;
        // 복제본은 원본과 같은 워크스페이스에 둔다. 현재 컨텍스트를 쓰면 원본과 갈라질 수 있다.
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
        return saveWithOwnership(copy, userId);
    }

    /**
     * 만든 사람에게 소유 권한을 함께 준다. 없으면 일반 구성원이 문서를 만드는 즉시 접근을 잃는다.
     * 만들었다는 사실은 접근 제어에 쓰이지 않기 때문이다 (FR-PRM-011).
     */
    private DocumentEntity saveWithOwnership(DocumentEntity document, String creatorId) {
        DocumentEntity saved = documentRepository.save(document);
        permissionService.grantCreatorOwnership(saved.getWorkspaceId(), ResourceType.DOCUMENT,
                saved.getDocumentId(), UUID.fromString(creatorId));
        return saved;
    }

    @Transactional
    public void delete(String documentId) {
        DocumentEntity doc = loadPermitted(documentId, PermissionAction.DELETE);
        doc.softDelete(requireUserId());
        documentRepository.save(doc);
        eventPublisher.publishEvent(new DocumentDeletedEvent(documentId));
    }

    @Transactional
    public List<DocumentEntity> reorder(String folderId, List<String> documentIds) {
        List<DocumentEntity> result = new ArrayList<>();
        for (int i = 0; i < documentIds.size(); i++) {
            DocumentEntity doc = loadPermitted(documentIds.get(i), PermissionAction.EDIT);
            doc.applyOrdinal(i);
            result.add(documentRepository.save(doc));
        }
        return result;
    }

    @Transactional
    public DocumentEntity move(String documentId, DocumentMoveRequest req) {
        DocumentEntity doc = loadPermitted(documentId, PermissionAction.EDIT);
        String targetFolderId = req.folderId();

        if (targetFolderId != null) {
            // 옮겨 넣을 폴더도 편집할 수 있어야 한다
            permissionEvaluator.require(ResourceType.FOLDER, targetFolderId, PermissionAction.EDIT);
            folderRepository.findByFolderIdAndUsable(targetFolderId, Integer.valueOf(1))
                    .filter(f -> f.getWorkspaceId().equals(doc.getWorkspaceId()))
                    .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));
        }

        int ordinal = documentRepository.maxOrdinal(targetFolderId, doc.getWorkspaceId()) + 1;
        doc.moveTo(targetFolderId, ordinal, requireUserId());
        return documentRepository.save(doc);
    }

    @Transactional(readOnly = true)
    public byte[] downloadPdf(String documentId) {
        return pdfGenerator.generate(loadForDownload(documentId));
    }

    public record PdfResult(String title, byte[] bytes) {}

    @Transactional(readOnly = true)
    public PdfResult downloadPdfResult(String documentId) {
        DocumentEntity doc = loadForDownload(documentId);
        return new PdfResult(doc.getTitle(), pdfGenerator.generate(doc));
    }

    /** 다운로드는 레벨이 아니라 별도 플래그로 막힌다 (FR-PRM-002). */
    private DocumentEntity loadForDownload(String documentId) {
        permissionEvaluator.requireDownload(ResourceType.DOCUMENT, documentId);
        return load(documentId);
    }

    private DocumentEntity loadPermitted(String documentId, PermissionAction action) {
        permissionEvaluator.require(ResourceType.DOCUMENT, documentId, action);
        return load(documentId);
    }

    private DocumentEntity load(String documentId) {
        return documentRepository
                .findByDocumentIdAndUsable(documentId, Integer.valueOf(1))
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "문서를 찾을 수 없습니다."));
    }

    private Page<DocumentEntity> paginate(List<DocumentEntity> items, Pageable pageable) {
        if (pageable.isUnpaged()) {
            return new PageImpl<>(items, pageable, items.size());
        }
        int from = (int) Math.min(pageable.getOffset(), items.size());
        int to = Math.min(from + pageable.getPageSize(), items.size());
        return new PageImpl<>(items.subList(from, to), pageable, items.size());
    }

    private String requireUserId() {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(Code.UNAUTHORIZED, "인증 정보가 없습니다.");
        }
        return userId;
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
