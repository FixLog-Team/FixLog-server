package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.domain.model.ResourceType;
import com.fixlog.domain.model.WorkspaceMemberEntity;
import com.fixlog.presentation.dto.response.EffectivePermissionDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin 콘솔에서 특정 사용자의 유효 권한과 출처를 계산한다.
 *
 * <p>Admin이 다른 사용자의 권한을 조회하므로 SecurityContext가 아닌 targetUserId를 직접 받는다.
 * 대규모 워크스페이스에서는 결과 수를 제한한다.
 */
@Service
public class EffectivePermissionService {

    private static final int MAX_RESOURCES = 500;

    private final PermissionEvaluator permissionEvaluator;
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceService workspaceService;

    public EffectivePermissionService(PermissionEvaluator permissionEvaluator,
                                      FolderRepository folderRepository,
                                      DocumentRepository documentRepository,
                                      WorkspaceMemberRepository memberRepository,
                                      WorkspaceService workspaceService) {
        this.permissionEvaluator = permissionEvaluator;
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.memberRepository = memberRepository;
        this.workspaceService = workspaceService;
    }

    /**
     * 특정 사용자의 워크스페이스 내 모든 리소스에 대한 유효 권한 목록.
     * Admin/Owner만 호출 가능하다.
     */
    @Transactional(readOnly = true)
    public List<EffectivePermissionDto> computeForUser(UUID workspaceId, UUID targetUserId) {
        workspaceService.requireAdmin(workspaceId);

        List<EffectivePermissionDto> result = new ArrayList<>();

        // 폴더
        List<FolderEntity> folders = folderRepository
                .findByWorkspaceIdAndUsable(workspaceId, Integer.valueOf(1))
                .stream().limit(MAX_RESOURCES / 2).toList();

        for (FolderEntity folder : folders) {
            try {
                PermissionEvaluator.DecisionWithSource dws =
                        permissionEvaluator.evaluateForWithSource(
                                targetUserId, ResourceType.FOLDER, folder.getFolderId());
                result.add(new EffectivePermissionDto(
                        ResourceType.FOLDER, folder.getFolderId(), folder.getFolderName(),
                        dws.decision().allowed(), dws.source(), dws.sourceDetail(),
                        dws.decision().canDownload()));
            } catch (Exception ignored) {
                // 구성원이 아닌 경우 등 예외 무시
            }
        }

        // 문서
        List<DocumentEntity> documents = documentRepository
                .findByWorkspaceIdAndUsable(workspaceId, Integer.valueOf(1),
                        org.springframework.data.domain.PageRequest.of(0, MAX_RESOURCES / 2))
                .getContent();

        for (DocumentEntity doc : documents) {
            try {
                PermissionEvaluator.DecisionWithSource dws =
                        permissionEvaluator.evaluateForWithSource(
                                targetUserId, ResourceType.DOCUMENT, doc.getDocumentId());
                result.add(new EffectivePermissionDto(
                        ResourceType.DOCUMENT, doc.getDocumentId(), doc.getTitle(),
                        dws.decision().allowed(), dws.source(), dws.sourceDetail(),
                        dws.decision().canDownload()));
            } catch (Exception ignored) {
            }
        }

        return result;
    }

    /**
     * 특정 리소스에 대한 특정 사용자의 유효 권한 단건 조회.
     */
    @Transactional(readOnly = true)
    public EffectivePermissionDto computeSingle(UUID workspaceId, UUID targetUserId,
                                                ResourceType resourceType, String resourceId) {
        workspaceService.requireAdmin(workspaceId);

        PermissionEvaluator.DecisionWithSource dws =
                permissionEvaluator.evaluateForWithSource(targetUserId, resourceType, resourceId);

        String resourceName = switch (resourceType) {
            case FOLDER -> folderRepository.findById(resourceId)
                    .map(FolderEntity::getFolderName).orElse(resourceId);
            case DOCUMENT -> documentRepository.findById(resourceId)
                    .map(DocumentEntity::getTitle).orElse(resourceId);
        };

        return new EffectivePermissionDto(
                resourceType, resourceId, resourceName,
                dws.decision().allowed(), dws.source(), dws.sourceDetail(),
                dws.decision().canDownload());
    }
}
