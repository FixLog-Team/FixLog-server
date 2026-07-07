package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.security.SecurityUtil;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.presentation.dto.request.FolderRequest;
import com.fixlog.presentation.dto.response.DocumentDto;
import com.fixlog.presentation.dto.response.FolderContentsDto;
import com.fixlog.presentation.dto.response.FolderDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        folder.updateFolder(request.parentId(), request.folderName(), request.ordinal(), userId);
        return folderRepository.save(folder);
    }

    @Transactional
    public void deleteFolder(String folderId) {
        String userId = requireUserId();

        FolderEntity folder = folderRepository.findByFolderIdAndCreateUser(folderId, userId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));

        folder.softDelete(userId);
        folderRepository.save(folder);
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
