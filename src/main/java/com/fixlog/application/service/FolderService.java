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
        if (request.workspaceId() == null || request.folderName() == null) {
            throw new BusinessException(Code.INVALID_REQUEST, "워크스페이스 ID와 폴더명은 필수입니다.");
        }

        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(Code.UNAUTHORIZED, "인증 정보가 없습니다.");
        }

        if (request.parentId() != null) {
            folderRepository.findByFolderIdAndWorkspaceId(request.parentId(), request.workspaceId())
                    .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "상위 폴더를 찾을 수 없습니다."));
        }

        String folderId = UUID.randomUUID().toString();
        FolderEntity folder = new FolderEntity(folderId, request.workspaceId(), request.parentId(), request.folderName(), userId);
        return folderRepository.save(folder);
    }

    @Transactional(readOnly = true)
    public List<FolderEntity> getFoldersByWorkspace(String workspaceId) {
        return folderRepository.findByWorkspaceIdAndUsable(workspaceId, 1);
    }

    @Transactional(readOnly = true)
    public FolderEntity getFolder(String folderId, String workspaceId) {
        return folderRepository.findByFolderIdAndWorkspaceId(folderId, workspaceId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));
    }

    @Transactional
    public FolderEntity updateFolder(String folderId, FolderRequest request) {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(Code.UNAUTHORIZED, "인증 정보가 없습니다.");
        }

        FolderEntity folder = folderRepository.findByFolderIdAndWorkspaceId(folderId, request.workspaceId())
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));

        folder.updateFolder(request, userId);
        return folderRepository.save(folder);
    }

    @Transactional
    public void deleteFolder(String folderId, String workspaceId) {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(Code.UNAUTHORIZED, "인증 정보가 없습니다.");
        }

        FolderEntity folder = folderRepository.findByFolderIdAndWorkspaceId(folderId, workspaceId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));

        folder.softDelete(userId);
        folderRepository.save(folder);
    }

    @Transactional(readOnly = true)
    public FolderContentsDto getRootContents(String workspaceId) {
        List<FolderDto> folders = folderRepository
                .findByParentIdIsNullAndWorkspaceIdAndUsable(workspaceId, 1)
                .stream().map(FolderDto::from).toList();

        return new FolderContentsDto(folders, List.of());
    }

    @Transactional(readOnly = true)
    public FolderContentsDto getFolderContents(String folderId, String workspaceId) {
        folderRepository.findByFolderIdAndWorkspaceId(folderId, workspaceId)
                .filter(f -> f.getUsable() == 1)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));

        List<FolderDto> folders = folderRepository
                .findByParentIdAndWorkspaceIdAndUsable(folderId, workspaceId, 1)
                .stream().map(FolderDto::from).toList();

        List<DocumentDto> documents = documentRepository
                .findByFolderIdAndWorkspaceIdAndUsable(folderId, workspaceId, 1)
                .stream().map(DocumentDto::from).toList();

        return new FolderContentsDto(folders, documents);
    }
}
