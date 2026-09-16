package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentFavoriteRepository;
import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.security.SecurityUtil;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.DocumentFavoriteEntity;
import com.fixlog.domain.model.PermissionAction;
import com.fixlog.domain.model.ResourceType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FavoriteService {

    private final DocumentFavoriteRepository favoriteRepository;
    private final DocumentRepository documentRepository;
    private final PermissionEvaluator permissionEvaluator;

    public FavoriteService(DocumentFavoriteRepository favoriteRepository,
                           DocumentRepository documentRepository,
                           PermissionEvaluator permissionEvaluator) {
        this.favoriteRepository = favoriteRepository;
        this.documentRepository = documentRepository;
        this.permissionEvaluator = permissionEvaluator;
    }

    /** 현재 사용자의 즐겨찾기 문서 목록 (최근 추가순). */
    @Transactional(readOnly = true)
    public List<DocumentEntity> list() {
        UUID userId = requireUserId();
        List<String> documentIds = favoriteRepository
                .findByUserIdOrderByCreateAtDesc(userId).stream()
                .map(DocumentFavoriteEntity::getDocumentId)
                .toList();
        return documentRepository.findAllById(documentIds).stream()
                .filter(doc -> Integer.valueOf(1).equals(doc.getUsable()))
                .toList();
    }

    /** 즐겨찾기 추가. 이미 추가되어 있으면 무시한다. */
    @Transactional
    public void add(String documentId) {
        permissionEvaluator.require(ResourceType.DOCUMENT, documentId, PermissionAction.VIEW);
        UUID userId = requireUserId();
        if (!favoriteRepository.existsByDocumentIdAndUserId(documentId, userId)) {
            favoriteRepository.save(new DocumentFavoriteEntity(documentId, userId));
        }
    }

    /** 즐겨찾기 제거. */
    @Transactional
    public void remove(String documentId) {
        UUID userId = requireUserId();
        DocumentFavoriteEntity favorite = favoriteRepository
                .findByDocumentIdAndUserId(documentId, userId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "즐겨찾기에 없는 문서입니다."));
        favoriteRepository.delete(favorite);
    }

    /** 특정 문서가 현재 사용자의 즐겨찾기인지 확인. */
    @Transactional(readOnly = true)
    public boolean isFavorited(String documentId) {
        UUID userId = requireUserId();
        return favoriteRepository.existsByDocumentIdAndUserId(documentId, userId);
    }

    private UUID requireUserId() {
        String id = SecurityUtil.getCurrentUserId();
        if (id == null) throw new BusinessException(Code.UNAUTHORIZED, "로그인이 필요합니다.");
        return UUID.fromString(id);
    }
}
