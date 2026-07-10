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
import com.fixlog.presentation.dto.response.FolderTreeDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FolderService {

    /** groupingBy는 null 키를 허용하지 않으므로 루트를 가리키는 대체 키. 폴더 ID로는 쓰이지 않는다. */
    private static final String ROOT_KEY = "";

    /** 형제 정렬 기준. Postgres의 ordinal asc, create_time asc 조회 순서와 동일하게 맞춘다(NULL은 뒤). */
    private static final Comparator<FolderEntity> TREE_ORDER =
            Comparator.comparing(FolderEntity::getOrdinal, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(FolderEntity::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()));

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
        FolderEntity folder = new FolderEntity(
                folderId, request.parentId(), request.folderName(), nextOrdinal(request.parentId(), userId), userId);
        return folderRepository.save(folder);
    }

    /** 같은 부모 안에서 마지막 폴더 다음 순번. 형제가 없으면 0. */
    private int nextOrdinal(String parentId, String userId) {
        return folderRepository.maxOrdinal(parentId, userId) + 1;
    }

    private List<FolderEntity> siblings(String parentId, String userId) {
        return parentId == null
                ? folderRepository.findByParentIdIsNullAndCreateUserAndUsableOrderByOrdinalAscCreateTimeAsc(
                        userId, Integer.valueOf(1))
                : folderRepository.findByParentIdAndCreateUserAndUsableOrderByOrdinalAscCreateTimeAsc(
                        parentId, userId, Integer.valueOf(1));
    }

    /**
     * 사이드바용 전체 폴더 트리. 폴더 전량과 폴더별 문서 수를 각각 한 번씩 조회한 뒤 메모리에서 엮는다.
     * 깊이만큼 {@code getFolderContents}를 재귀 호출하지 않기 위한 엔드포인트다.
     */
    @Transactional(readOnly = true)
    public List<FolderTreeDto> getFolderTree() {
        String userId = requireUserId();

        List<FolderEntity> folders = folderRepository.findByCreateUserAndUsable(userId, Integer.valueOf(1));

        Map<String, Long> documentCounts = documentRepository.countDocumentsByFolder(userId).stream()
                .collect(Collectors.toMap(
                        DocumentRepository.FolderDocumentCount::getFolderId,
                        DocumentRepository.FolderDocumentCount::getDocumentCount));

        // 루트 폴더는 parentId가 null이므로 groupingBy에 넣을 수 없다. 빈 문자열을 루트 키로 쓴다.
        Map<String, List<FolderEntity>> childrenByParent = folders.stream()
                .collect(Collectors.groupingBy(f -> f.getParentId() == null ? ROOT_KEY : f.getParentId()));

        return buildNodes(ROOT_KEY, childrenByParent, documentCounts);
    }

    private List<FolderTreeDto> buildNodes(String parentKey,
                                           Map<String, List<FolderEntity>> childrenByParent,
                                           Map<String, Long> documentCounts) {
        return childrenByParent.getOrDefault(parentKey, List.of()).stream()
                .sorted(TREE_ORDER)
                .map(folder -> new FolderTreeDto(
                        folder.getFolderId(),
                        folder.getParentId(),
                        folder.getFolderName(),
                        folder.getOrdinal(),
                        documentCounts.getOrDefault(folder.getFolderId(), 0L),
                        buildNodes(folder.getFolderId(), childrenByParent, documentCounts)))
                .toList();
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

        // 부모 변경은 moveFolder, 순서 변경은 reorderFolders에서만 처리한다.
        folder.rename(request.folderName(), userId);
        return folderRepository.save(folder);
    }

    /**
     * 같은 부모 아래 폴더들의 순서를 folderIds 순서대로 다시 매긴다.
     * folderIds는 해당 부모의 활성 폴더 전체와 정확히 일치해야 한다. 일부만 보내면 순번에 구멍이나 중복이 생긴다.
     */
    @Transactional
    public List<FolderEntity> reorderFolders(String parentId, List<String> folderIds) {
        String userId = requireUserId();

        if (folderIds == null || folderIds.isEmpty()) {
            throw new BusinessException(Code.INVALID_REQUEST, "정렬할 폴더 목록이 비어 있습니다.");
        }

        Set<String> requested = new LinkedHashSet<>(folderIds);
        if (requested.size() != folderIds.size()) {
            throw new BusinessException(Code.INVALID_REQUEST, "폴더 목록에 중복된 항목이 있습니다.");
        }

        if (parentId != null) {
            folderRepository.findByFolderIdAndCreateUser(parentId, userId)
                    .filter(f -> Integer.valueOf(1).equals(f.getUsable()))
                    .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "상위 폴더를 찾을 수 없습니다."));
        }

        List<FolderEntity> current = siblings(parentId, userId);
        if (current.size() != requested.size()) {
            throw new BusinessException(Code.INVALID_REQUEST, "해당 폴더의 하위 폴더 전체를 순서대로 보내야 합니다.");
        }

        Map<String, FolderEntity> byId = current.stream()
                .collect(Collectors.toMap(FolderEntity::getFolderId, Function.identity()));

        List<FolderEntity> reordered = new ArrayList<>();
        int ordinal = 0;
        for (String folderId : requested) {
            FolderEntity folder = byId.get(folderId);
            if (folder == null) {
                throw new BusinessException(Code.INVALID_REQUEST, "해당 위치에 속하지 않은 폴더가 포함되어 있습니다.");
            }
            folder.applyOrdinal(ordinal++);
            reordered.add(folder);
        }
        return folderRepository.saveAll(reordered);
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

        // 새 부모의 형제들과 순번이 겹치지 않도록 맨 끝으로 보낸다.
        folder.moveTo(newParentId, nextOrdinal(newParentId, userId), userId);
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
            List<FolderEntity> children = folderRepository
                    .findByParentIdAndCreateUserAndUsableOrderByOrdinalAscCreateTimeAsc(
                            queue.poll(), userId, Integer.valueOf(1));
            for (FolderEntity child : children) {
                subtree.add(child);
                queue.add(child.getFolderId());
            }
        }

        // 폴더 트리와 그 안의 문서를 모두 소프트 삭제한다.
        List<DocumentEntity> documents = new ArrayList<>();
        for (FolderEntity f : subtree) {
            f.softDelete(userId);
            for (DocumentEntity doc : documentRepository
                    .findByFolderIdAndCreateUserAndUsableOrderByOrdinalAscCreateTimeAsc(f.getFolderId(), userId, 1)) {
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
        List<FolderDto> folders = siblings(null, userId).stream().map(FolderDto::from).toList();

        List<DocumentDto> documents = documentRepository
                .findByFolderIdIsNullAndCreateUserAndUsableOrderByOrdinalAscCreateTimeAsc(userId, 1)
                .stream().map(DocumentDto::from).toList();

        return new FolderContentsDto(folders, documents);
    }

    @Transactional(readOnly = true)
    public FolderContentsDto getFolderContents(String folderId) {
        String userId = requireUserId();
        folderRepository.findByFolderIdAndCreateUser(folderId, userId)
                .filter(f -> Integer.valueOf(1).equals(f.getUsable()))
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));

        List<FolderDto> folders = siblings(folderId, userId).stream().map(FolderDto::from).toList();

        List<DocumentDto> documents = documentRepository
                .findByFolderIdAndCreateUserAndUsableOrderByOrdinalAscCreateTimeAsc(folderId, userId, 1)
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
