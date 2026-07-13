package com.fixlog.application.service;

import tools.jackson.databind.JsonNode;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final DocumentTextExtractor textExtractor;
    private final DocumentPdfGenerator pdfGenerator;

    public DocumentService(DocumentRepository documentRepository,
                           FolderRepository folderRepository,
                           DocumentTextExtractor textExtractor,
                           DocumentPdfGenerator pdfGenerator) {
        this.documentRepository = documentRepository;
        this.folderRepository = folderRepository;
        this.textExtractor = textExtractor;
        this.pdfGenerator = pdfGenerator;
    }

    @Transactional
    public DocumentEntity create(DocumentCreateRequest req) {
        String userId = requireUserId();
        String folderId = req.folderId();
        if (folderId != null) {
            folderRepository.findByFolderIdAndCreateUser(folderId, userId)
                    .filter(f -> Integer.valueOf(1).equals(f.getUsable()))
                    .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));
        }
        String title = (req.title() == null || req.title().isBlank()) ? "제목 없음" : req.title();
        String documentId = UUID.randomUUID().toString();
        DocumentEntity doc = new DocumentEntity(
                documentId, folderId, title, "[]", "", null, nextOrdinal(folderId, userId), userId);
        return documentRepository.save(doc);
    }

    /** 같은 폴더 안에서 마지막 문서 다음 순번. 문서가 없으면 0. */
    private int nextOrdinal(String folderId, String userId) {
        return documentRepository.maxOrdinal(folderId, userId) + 1;
    }

    @Transactional(readOnly = true)
    public Page<DocumentEntity> list(String folderId, Pageable pageable) {
        String userId = requireUserId();
        if (folderId != null) {
            return documentRepository.findByFolderIdAndCreateUserAndUsable(folderId, userId, 1, pageable);
        }
        return documentRepository.findByCreateUserAndUsable(userId, 1, pageable);
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
                original.getFolderId(),
                original.getTitle() + " (1)",
                original.getBlocks(),
                original.getPlainText(),
                original.getContentHash(),
                nextOrdinal(original.getFolderId(), userId),
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
        String userId = requireUserId();
        DocumentEntity doc = loadOwned(documentId);
        String targetFolderId = req.folderId();
        if (targetFolderId != null) {
            folderRepository.findByFolderIdAndCreateUser(targetFolderId, userId)
                    .filter(f -> Integer.valueOf(1).equals(f.getUsable()))
                    .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));
        }
        // 새 폴더의 문서들과 순번이 겹치지 않도록 맨 끝으로 보낸다.
        doc.moveTo(targetFolderId, nextOrdinal(targetFolderId, userId), userId);
        return documentRepository.save(doc);
    }

    /**
     * 같은 폴더 안 문서들의 순서를 documentIds 순서대로 다시 매긴다.
     * documentIds는 해당 폴더의 활성 문서 전체와 정확히 일치해야 한다.
     */
    @Transactional
    public List<DocumentEntity> reorder(String folderId, List<String> documentIds) {
        String userId = requireUserId();

        if (documentIds == null || documentIds.isEmpty()) {
            throw new BusinessException(Code.INVALID_REQUEST, "정렬할 문서 목록이 비어 있습니다.");
        }

        Set<String> requested = new LinkedHashSet<>(documentIds);
        if (requested.size() != documentIds.size()) {
            throw new BusinessException(Code.INVALID_REQUEST, "문서 목록에 중복된 항목이 있습니다.");
        }

        if (folderId != null) {
            folderRepository.findByFolderIdAndCreateUser(folderId, userId)
                    .filter(f -> Integer.valueOf(1).equals(f.getUsable()))
                    .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));
        }

        List<DocumentEntity> current = folderId == null
                ? documentRepository.findByFolderIdIsNullAndCreateUserAndUsableOrderByOrdinalAscCreateTimeAsc(userId, 1)
                : documentRepository.findByFolderIdAndCreateUserAndUsableOrderByOrdinalAscCreateTimeAsc(
                        folderId, userId, 1);
        if (current.size() != requested.size()) {
            throw new BusinessException(Code.INVALID_REQUEST, "해당 폴더의 문서 전체를 순서대로 보내야 합니다.");
        }

        Map<String, DocumentEntity> byId = current.stream()
                .collect(Collectors.toMap(DocumentEntity::getDocumentId, Function.identity()));

        List<DocumentEntity> reordered = new ArrayList<>();
        int ordinal = 0;
        for (String documentId : requested) {
            DocumentEntity doc = byId.get(documentId);
            if (doc == null) {
                throw new BusinessException(Code.INVALID_REQUEST, "해당 폴더에 속하지 않은 문서가 포함되어 있습니다.");
            }
            doc.applyOrdinal(ordinal++);
            reordered.add(doc);
        }
        return documentRepository.saveAll(reordered);
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
