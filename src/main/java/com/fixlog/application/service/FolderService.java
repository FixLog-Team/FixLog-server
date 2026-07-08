package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.security.SecurityUtil;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.presentation.dto.request.FolderRequest;
import com.fixlog.presentation.dto.response.DocumentDto;
import com.fixlog.presentation.dto.response.FolderContentsDto;
import com.fixlog.presentation.dto.response.FolderDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

@Service
public class FolderService {
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;

    public FolderService(FolderRepository folderRepository, DocumentRepository documentRepository) {
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
    }

    @Transactional
    public FolderEntity createFolder(FolderRequest request) {
        if (request.folderName() == null) {
            throw new BusinessException(Code.INVALID_REQUEST, "폴더명은 필수입니다.");
        }

        String userId = requireUserId();

        if (request.parentId() != null) {
            folderRepository.findByFolderIdAndCreateUser(request.parentId(), userId)
                    .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "상위 폴더를 찾을 수 없습니다."));
        }

        String folderId = UUID.randomUUID().toString();
        FolderEntity folder = new FolderEntity(folderId, request.parentId(), request.folderName(), userId);
        return folderRepository.save(folder);
    }

    @Transactional(readOnly = true)
    public List<FolderEntity> getFolders() {
        String userId = requireUserId();
        return folderRepository.findByCreateUserAndUsable(userId, Integer.valueOf(1));
    }

    @Transactional(readOnly = true)
    public FolderEntity getFolder(String folderId) {
        String userId = requireUserId();
        return folderRepository.findByFolderIdAndCreateUser(folderId, userId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));
    }

    @Transactional
    public FolderEntity updateFolder(String folderId, FolderRequest request) {
        String userId = requireUserId();

        FolderEntity folder = folderRepository.findByFolderIdAndCreateUser(folderId, userId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));

        // 부모 변경은 moveFolder에서만 처리한다. 여기서는 이름/순서만 수정한다.
        folder.updateFolder(null, request.folderName(), request.ordinal(), userId);
        return folderRepository.save(folder);
    }

    @Transactional
    public FolderEntity moveFolder(String folderId, String newParentId) {
        String userId = requireUserId();

        FolderEntity folder = folderRepository.findByFolderIdAndCreateUser(folderId, userId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));

        if (newParentId != null) {
            if (newParentId.equals(folderId)) {
                throw new BusinessException(Code.INVALID_REQUEST, "폴더를 자기 자신으로 이동할 수 없습니다.");
            }
            folderRepository.findByFolderIdAndCreateUser(newParentId, userId)
                    .filter(f -> Integer.valueOf(1).equals(f.getUsable()))
                    .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "이동할 상위 폴더를 찾을 수 없습니다."));
            if (isDescendantOf(folderId, newParentId, userId)) {
                throw new BusinessException(Code.INVALID_REQUEST, "폴더를 자신의 하위 폴더로 이동할 수 없습니다.");
            }
        }

        folder.moveTo(newParentId, userId);
        return folderRepository.save(folder);
    }

    @Transactional
    public void deleteFolder(String folderId) {
        String userId = requireUserId();

        FolderEntity folder = folderRepository.findByFolderIdAndCreateUser(folderId, userId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));

        // 대상 폴더와 모든 하위 폴더 트리를 수집한다.
        List<FolderEntity> subtree = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        subtree.add(folder);
        queue.add(folder.getFolderId());
        while (!queue.isEmpty()) {
            List<FolderEntity> children =
                    folderRepository.findByParentIdAndCreateUserAndUsable(queue.poll(), userId, Integer.valueOf(1));
            for (FolderEntity child : children) {
                subtree.add(child);
                queue.add(child.getFolderId());
            }
        }

        // 폴더 트리와 그 안의 문서를 모두 소프트 삭제한다.
        List<DocumentEntity> documents = new ArrayList<>();
        for (FolderEntity f : subtree) {
            f.softDelete(userId);
            for (DocumentEntity doc :
                    documentRepository.findByFolderIdAndCreateUserAndUsable(f.getFolderId(), userId, 1)) {
                doc.softDelete(userId);
                documents.add(doc);
            }
        }
        folderRepository.saveAll(subtree);
        documentRepository.saveAll(documents);
    }

    private boolean isDescendantOf(String ancestorId, String candidateId, String userId) {
        String current = candidateId;
        int guard = 0;
        while (current != null && guard++ < 10000) {
            if (current.equals(ancestorId)) {
                return true;
            }
            current = folderRepository.findByFolderIdAndCreateUser(current, userId)
                    .map(FolderEntity::getParentId)
                    .orElse(null);
        }
        return false;
    }

    @Transactional(readOnly = true)
    public FolderContentsDto getRootContents() {
        String userId = requireUserId();
        List<FolderDto> folders = folderRepository
                .findByParentIdIsNullAndCreateUserAndUsable(userId, Integer.valueOf(1))
                .stream().map(FolderDto::from).toList();

        List<DocumentDto> documents = documentRepository
                .findByFolderIdIsNullAndCreateUserAndUsable(userId, 1)
                .stream().map(DocumentDto::from).toList();

        return new FolderContentsDto(folders, documents);
    }

    @Transactional(readOnly = true)
    public FolderContentsDto getFolderContents(String folderId) {
        String userId = requireUserId();
        folderRepository.findByFolderIdAndCreateUser(folderId, userId)
                .filter(f -> Integer.valueOf(1).equals(f.getUsable()))
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));

        List<FolderDto> folders = folderRepository
                .findByParentIdAndCreateUserAndUsable(folderId, userId, Integer.valueOf(1))
                .stream().map(FolderDto::from).toList();

        List<DocumentDto> documents = documentRepository
                .findByFolderIdAndCreateUserAndUsable(folderId, userId, 1)
                .stream().map(DocumentDto::from).toList();

        return new FolderContentsDto(folders, documents);
    }

    private String requireUserId() {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(Code.UNAUTHORIZED, "인증 정보가 없습니다.");
        }
        return userId;
    }
}
