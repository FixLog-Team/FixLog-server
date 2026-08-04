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
import com.fixlog.presentation.dto.request.DocumentCreateRequest;
import com.fixlog.presentation.dto.request.DocumentMoveRequest;
import com.fixlog.presentation.dto.request.DocumentSaveRequest;
import com.fixlog.presentation.dto.request.DocumentTitleRequest;
import com.fixlog.presentation.dto.response.DocumentSaveStateDto;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final DocumentTextExtractor textExtractor;
    private final DocumentPdfGenerator pdfGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final WorkspaceContext workspaceContext;

    public DocumentService(DocumentRepository documentRepository,
                           FolderRepository folderRepository,
                           DocumentTextExtractor textExtractor,
                           DocumentPdfGenerator pdfGenerator,
                           ApplicationEventPublisher eventPublisher,
                           WorkspaceContext workspaceContext) {
        this.workspaceContext = workspaceContext;
        this.documentRepository = documentRepository;
        this.folderRepository = folderRepository;
        this.textExtractor = textExtractor;
        this.pdfGenerator = pdfGenerator;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public DocumentEntity create(DocumentCreateRequest req) {
        String userId = requireUserId();
        int ordinal = documentRepository.maxOrdinal(req.folderId(), userId) + 1;
        DocumentEntity doc = new DocumentEntity(
                UUID.randomUUID().toString(),
                workspaceContext.requireCurrentWorkspaceId(),
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

    @Transactional(readOnly = true)
    public Page<DocumentEntity> list(String folderId, Pageable pageable) {
        String userId = requireUserId();
        if (folderId != null) {
            return documentRepository.findByFolderIdAndCreateUserAndUsable(folderId, userId, Integer.valueOf(1), pageable);
        }
        return documentRepository.findByCreateUserAndUsable(userId, Integer.valueOf(1), pageable);
    }

    @Transactional(readOnly = true)
    public DocumentEntity getDocument(String documentId) {
        return loadOwned(documentId);
    }

    @Transactional
    public DocumentEntity saveContent(String documentId, DocumentSaveRequest req) {
        DocumentEntity doc = loadOwned(documentId);
        textExtractor.validateBlocks(req.blocks());
        String canonicalJson = normalizeBlocks(req.blocks());
        String plainText = textExtractor.extract(canonicalJson);
        String hash = hashContent(canonicalJson);
        boolean contentChanged = !hash.equals(doc.getContentHash());
        boolean titleChanged = !java.util.Objects.equals(req.title(), doc.getTitle());
        doc.updateContent(req.title(), canonicalJson, plainText, hash, requireUserId());
        DocumentEntity saved = documentRepository.save(doc);
        eventPublisher.publishEvent(new DocumentSavedEvent(saved, contentChanged, titleChanged));
        return saved;
    }

    @Transactional
    public DocumentEntity updateTitle(String documentId, DocumentTitleRequest req) {
        DocumentEntity doc = loadOwned(documentId);
        doc.updateTitle(req.title(), requireUserId());
        return documentRepository.save(doc);
    }

    @Transactional(readOnly = true)
    public DocumentSaveStateDto getSaveState(String documentId) {
        return DocumentSaveStateDto.from(loadOwned(documentId));
    }

    @Transactional
    public DocumentEntity duplicate(String documentId) {
        DocumentEntity original = loadOwned(documentId);
        String userId = requireUserId();
        int ordinal = documentRepository.maxOrdinal(original.getFolderId(), userId) + 1;
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
        return documentRepository.save(copy);
    }

    @Transactional
    public void delete(String documentId) {
        DocumentEntity doc = loadOwned(documentId);
        doc.softDelete(requireUserId());
        documentRepository.save(doc);
        eventPublisher.publishEvent(new DocumentDeletedEvent(documentId));
    }

    @Transactional
    public List<DocumentEntity> reorder(String folderId, List<String> documentIds) {
        String userId = requireUserId();
        List<DocumentEntity> result = new ArrayList<>();
        for (int i = 0; i < documentIds.size(); i++) {
            DocumentEntity doc = documentRepository
                    .findByDocumentIdAndCreateUserAndUsable(documentIds.get(i), userId, Integer.valueOf(1))
                    .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "문서를 찾을 수 없습니다."));
            doc.applyOrdinal(i);
            result.add(documentRepository.save(doc));
        }
        return result;
    }

    @Transactional
    public DocumentEntity move(String documentId, DocumentMoveRequest req) {
        String userId = requireUserId();
        DocumentEntity doc = loadOwned(documentId);
        String targetFolderId = req.folderId();
        if (targetFolderId != null) {
            folderRepository.findByFolderIdAndCreateUser(targetFolderId, userId)
                    .filter(f -> Integer.valueOf(1).equals(f.getUsable()))
                    .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));
        }
        int ordinal = documentRepository.maxOrdinal(targetFolderId, userId) + 1;
        doc.moveTo(targetFolderId, ordinal, userId);
        return documentRepository.save(doc);
    }

    @Transactional(readOnly = true)
    public byte[] downloadPdf(String documentId) {
        DocumentEntity doc = loadOwned(documentId);
        return pdfGenerator.generate(doc);
    }

    public record PdfResult(String title, byte[] bytes) {}

    @Transactional(readOnly = true)
    public PdfResult downloadPdfResult(String documentId) {
        DocumentEntity doc = loadOwned(documentId);
        return new PdfResult(doc.getTitle(), pdfGenerator.generate(doc));
    }

    private DocumentEntity loadOwned(String documentId) {
        String userId = requireUserId();
        return documentRepository
                .findByDocumentIdAndCreateUserAndUsable(documentId, userId, Integer.valueOf(1))
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "문서를 찾을 수 없습니다."));
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
