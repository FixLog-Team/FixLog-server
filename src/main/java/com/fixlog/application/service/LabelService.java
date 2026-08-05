package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentLabelRepository;
import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.LabelRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.security.SecurityUtil;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.DocumentLabelEntity;
import com.fixlog.domain.model.LabelEntity;
import com.fixlog.domain.model.PermissionAction;
import com.fixlog.domain.model.ResourceType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 라벨 (FR-LBL-001~007).
 *
 * <p>라벨 자체는 워크스페이스 공용이지만, <b>라벨로 찾은 결과는 권한을 통과한 문서만</b> 담는다.
 * 라벨이 권한 우회 통로가 되면 안 된다.
 */
@Service
public class LabelService {

    private final LabelRepository labelRepository;
    private final DocumentLabelRepository documentLabelRepository;
    private final DocumentRepository documentRepository;
    private final WorkspaceContext workspaceContext;
    private final PermissionEvaluator permissionEvaluator;

    public LabelService(LabelRepository labelRepository,
                        DocumentLabelRepository documentLabelRepository,
                        DocumentRepository documentRepository,
                        WorkspaceContext workspaceContext,
                        PermissionEvaluator permissionEvaluator) {
        this.labelRepository = labelRepository;
        this.documentLabelRepository = documentLabelRepository;
        this.documentRepository = documentRepository;
        this.workspaceContext = workspaceContext;
        this.permissionEvaluator = permissionEvaluator;
    }

    @Transactional(readOnly = true)
    public List<LabelEntity> labelsOfWorkspace() {
        return labelRepository.findByWorkspaceIdOrderByLabelNameAsc(
                workspaceContext.requireCurrentWorkspaceId());
    }

    @Transactional(readOnly = true)
    public List<LabelEntity> labelsOf(String documentId) {
        permissionEvaluator.require(ResourceType.DOCUMENT, documentId, PermissionAction.VIEW);
        return labelRepository.findAllById(
                documentLabelRepository.findByDocumentId(documentId).stream()
                        .map(DocumentLabelEntity::getLabelId).toList());
    }

    /** 라벨 부여. 없는 이름이면 워크스페이스 라벨로 새로 만든다 (FR-LBL-002). */
    @Transactional
    public LabelEntity attach(String documentId, String labelName) {
        permissionEvaluator.require(ResourceType.DOCUMENT, documentId, PermissionAction.EDIT);
        UUID workspaceId = documentWorkspace(documentId);
        String name = requireName(labelName);

        LabelEntity label = labelRepository.findByWorkspaceIdAndLabelName(workspaceId, name)
                .orElseGet(() -> labelRepository.save(new LabelEntity(workspaceId, name)));

        if (documentLabelRepository.findByDocumentIdAndLabelId(documentId, label.getId()).isEmpty()) {
            documentLabelRepository.save(
                    new DocumentLabelEntity(documentId, label.getId(), SecurityUtil.getCurrentUserId()));
        }
        return label;
    }

    @Transactional
    public void detach(String documentId, UUID labelId) {
        permissionEvaluator.require(ResourceType.DOCUMENT, documentId, PermissionAction.EDIT);

        DocumentLabelEntity mapping = documentLabelRepository
                .findByDocumentIdAndLabelId(documentId, labelId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "문서에 붙은 라벨이 아닙니다."));
        documentLabelRepository.delete(mapping);
    }

    /** 라벨로 문서 찾기. 권한을 통과한 문서만 돌려준다 (FR-LBL-004, 007). */
    @Transactional(readOnly = true)
    public List<DocumentEntity> documentsWith(UUID labelId) {
        UUID workspaceId = workspaceContext.requireCurrentWorkspaceId();
        labelRepository.findByIdAndWorkspaceId(labelId, workspaceId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "라벨을 찾을 수 없습니다."));

        PermissionEvaluator.Scope scope = permissionEvaluator.scopeFor(workspaceId);
        List<String> documentIds = documentLabelRepository.findByLabelId(labelId).stream()
                .map(DocumentLabelEntity::getDocumentId).toList();

        return documentRepository.findAllById(documentIds).stream()
                .filter(doc -> Integer.valueOf(1).equals(doc.getUsable()))
                .filter(doc -> doc.getWorkspaceId().equals(workspaceId))
                .filter(doc -> scope.canViewDocument(doc.getDocumentId(), doc.getFolderId()))
                .toList();
    }

    private UUID documentWorkspace(String documentId) {
        return documentRepository.findByDocumentIdAndUsable(documentId, Integer.valueOf(1))
                .map(DocumentEntity::getWorkspaceId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "문서를 찾을 수 없습니다."));
    }

    private String requireName(String labelName) {
        if (labelName == null || labelName.isBlank()) {
            throw new BusinessException(Code.INVALID_REQUEST, "라벨 이름은 필수입니다.");
        }
        return labelName.trim();
    }
}
