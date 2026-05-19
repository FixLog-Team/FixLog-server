package com.fixlog.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.security.SecurityUtil;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.presentation.dto.request.DocumentMoveRequest;
import com.fixlog.presentation.dto.request.DocumentSaveRequest;
import com.fixlog.presentation.dto.request.DocumentTitleRequest;
import com.fixlog.presentation.dto.response.DocumentSaveStateDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final DocumentTextExtractor textExtractor;
    private final DocumentPdfGenerator pdfGenerator;

    private final ObjectMapper canonicalMapper;

    public DocumentService(DocumentRepository documentRepository,
                           FolderRepository folderRepository,
                           DocumentTextExtractor textExtractor,
                           DocumentPdfGenerator pdfGenerator) {
        this.documentRepository = documentRepository;
        this.folderRepository = folderRepository;
        this.textExtractor = textExtractor;
        this.pdfGenerator = pdfGenerator;
        this.canonicalMapper = new ObjectMapper()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
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
        doc.updateContent(req.title(), canonicalJson, plainText, hash, requireUserId());
        return documentRepository.save(doc);
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
        String newId = UUID.randomUUID().toString();
        DocumentEntity copy = new DocumentEntity(
                newId,
                original.getWorkspaceId(),
                original.getFolderId(),
                original.getTitle() + " (1)",
                original.getBlocks(),
                original.getPlainText(),
                original.getContentHash(),
                userId
        );
        return documentRepository.save(copy);
    }

    @Transactional
    public void delete(String documentId) {
        DocumentEntity doc = loadOwned(documentId);
        doc.softDelete(requireUserId());
        documentRepository.save(doc);
    }

    @Transactional
    public DocumentEntity move(String documentId, DocumentMoveRequest req) {
        DocumentEntity doc = loadOwned(documentId);
        String targetFolderId = req.folderId();
        if (targetFolderId != null) {
            folderRepository.findByFolderIdAndWorkspaceId(targetFolderId, doc.getWorkspaceId())
                    .filter(f -> Integer.valueOf(1).equals(f.getUsable()))
                    .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));
        }
        doc.moveTo(targetFolderId, requireUserId());
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
        try {
            return canonicalMapper.writeValueAsString(blocks);
        } catch (Exception e) {
            throw new BusinessException(Code.INVALID_REQUEST, "blocks 형식이 올바르지 않습니다.");
        }
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
