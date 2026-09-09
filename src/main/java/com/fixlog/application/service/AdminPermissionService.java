package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.GroupRepository;
import com.fixlog.application.repository.PermissionRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.AuditAction;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.domain.model.PermissionEntity;
import com.fixlog.domain.model.PermissionType;
import com.fixlog.domain.model.PrincipalType;
import com.fixlog.domain.model.ResourceType;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.WorkspaceMemberEntity;
import com.fixlog.presentation.dto.response.AdminPermissionDto;
import com.fixlog.presentation.dto.response.ResourcePermissionsDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin 전용 권한 CRUD. PermissionEvaluator의 SHARE 액션 체크를 우회하여
 * Admin/Owner가 워크스페이스 내 모든 리소스의 권한을 직접 관리할 수 있다.
 */
@Service
public class AdminPermissionService {

    private final PermissionRepository permissionRepository;
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final WorkspaceService workspaceService;
    private final AuditService auditService;

    public AdminPermissionService(PermissionRepository permissionRepository,
                                  FolderRepository folderRepository,
                                  DocumentRepository documentRepository,
                                  UserRepository userRepository,
                                  GroupRepository groupRepository,
                                  WorkspaceService workspaceService,
                                  AuditService auditService) {
        this.permissionRepository = permissionRepository;
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.workspaceService = workspaceService;
        this.auditService = auditService;
    }

    /** 리소스 권한 목록 + 폴더 상속 설정. */
    @Transactional(readOnly = true)
    public ResourcePermissionsDto listForResource(UUID workspaceId, ResourceType resourceType,
                                                  String resourceId) {
        workspaceService.requireAdmin(workspaceId);

        List<PermissionEntity> perms = permissionRepository.findByResourceTypeAndResourceId(
                resourceType, resourceId);
        List<AdminPermissionDto> dtos = describe(perms);

        if (resourceType == ResourceType.FOLDER) {
            FolderEntity folder = folderRepository.findById(resourceId)
                    .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));
            return new ResourcePermissionsDto(dtos, folder.isInheritFromParent(), folder.getBaseAccess());
        }
        return new ResourcePermissionsDto(dtos, null, null);
    }

    /** 폴더 상속·기본 접근 설정 변경. */
    @Transactional
    public void updateFolderSettings(UUID workspaceId, String folderId,
                                     boolean inheritFromParent, PermissionType baseAccess) {
        WorkspaceMemberEntity admin = workspaceService.requireAdmin(workspaceId);
        FolderEntity folder = folderRepository.findById(folderId)
                .filter(f -> f.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다."));
        String before = "inherit=" + folder.isInheritFromParent() + ", base=" + folder.getBaseAccess();
        folder.configureInheritance(inheritFromParent, baseAccess);
        folderRepository.save(folder);

        auditService.recordChange(workspaceId, admin.getUserId(),
                AuditAction.ACCESS_POLICY_CHANGE, ResourceType.FOLDER, folderId,
                null, null,
                before + " → inherit=" + inheritFromParent + ", base=" + baseAccess);
    }

    /** Admin이 직접 권한을 부여한다. 주체·대상이 같은 워크스페이스인지 확인한다. */
    @Transactional
    public AdminPermissionDto grant(UUID workspaceId, ResourceType resourceType, String resourceId,
                                    PrincipalType principalType, UUID principalId,
                                    PermissionType permissionType, boolean canDownload) {
        WorkspaceMemberEntity admin = workspaceService.requireAdmin(workspaceId);
        requireResourceInWorkspace(workspaceId, resourceType, resourceId);

        UUID granter = admin.getUserId();

        PermissionEntity saved = permissionRepository
                .findByResourceTypeAndResourceIdAndPrincipalTypeAndPrincipalId(
                        resourceType, resourceId, principalType, principalId)
                .map(existing -> {
                    existing.update(permissionType, canDownload);
                    return permissionRepository.save(existing);
                })
                .orElseGet(() -> permissionRepository.save(new PermissionEntity(
                        workspaceId, principalType, principalId,
                        resourceType, resourceId, permissionType, canDownload, granter)));

        auditService.recordChange(workspaceId, granter, AuditAction.PERMISSION_GRANT,
                resourceType, resourceId, principalType, principalId,
                permissionType + (canDownload ? " (다운로드 허용)" : " (다운로드 불가)"));

        return AdminPermissionDto.of(saved, principalNameOf(saved), resourceNameOf(resourceType, resourceId));
    }

    /** 기존 권한의 타입·다운로드 허용 여부를 변경한다. */
    @Transactional
    public AdminPermissionDto updatePermission(UUID workspaceId, UUID permissionId,
                                               PermissionType permissionType, boolean canDownload) {
        WorkspaceMemberEntity admin = workspaceService.requireAdmin(workspaceId);
        PermissionEntity permission = permissionRepository.findById(permissionId)
                .filter(p -> p.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "권한을 찾을 수 없습니다."));
        String before = permission.getPermissionType()
                + (permission.isCanDownload() ? " (다운로드 허용)" : " (다운로드 불가)");
        permission.update(permissionType, canDownload);
        permissionRepository.save(permission);

        auditService.recordChange(workspaceId, admin.getUserId(),
                AuditAction.PERMISSION_GRANT,
                permission.getResourceType(), permission.getResourceId(),
                permission.getPrincipalType(), permission.getPrincipalId(),
                before + " → " + permissionType
                        + (canDownload ? " (다운로드 허용)" : " (다운로드 불가)"));

        return AdminPermissionDto.of(permission,
                principalNameOf(permission),
                resourceNameOf(permission.getResourceType(), permission.getResourceId()));
    }

    /** 권한을 삭제한다. */
    @Transactional
    public void deletePermission(UUID workspaceId, UUID permissionId) {
        WorkspaceMemberEntity admin = workspaceService.requireAdmin(workspaceId);
        PermissionEntity permission = permissionRepository.findById(permissionId)
                .filter(p -> p.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "권한을 찾을 수 없습니다."));
        permissionRepository.delete(permission);

        auditService.recordChange(workspaceId, admin.getUserId(),
                AuditAction.PERMISSION_REVOKE,
                permission.getResourceType(), permission.getResourceId(),
                permission.getPrincipalType(), permission.getPrincipalId(),
                "회수됨 (" + permission.getPermissionType() + ")");
    }

    private void requireResourceInWorkspace(UUID workspaceId, ResourceType resourceType, String resourceId) {
        boolean inWorkspace = switch (resourceType) {
            case FOLDER -> folderRepository.findById(resourceId)
                    .map(f -> f.getWorkspaceId().equals(workspaceId))
                    .orElse(false);
            case DOCUMENT -> documentRepository.findById(resourceId)
                    .map(d -> d.getWorkspaceId().equals(workspaceId))
                    .orElse(false);
        };
        if (!inWorkspace) {
            throw new BusinessException(Code.NOT_FOUND, "리소스를 찾을 수 없습니다.");
        }
    }

    private List<AdminPermissionDto> describe(List<PermissionEntity> permissions) {
        Map<UUID, String> principalNames = new HashMap<>();
        permissions.forEach(p -> principalNames.computeIfAbsent(p.getPrincipalId(),
                id -> principalNameOf(p)));
        Map<String, String> resourceNames = new HashMap<>();
        permissions.forEach(p -> resourceNames.computeIfAbsent(
                p.getResourceType().name() + ":" + p.getResourceId(),
                key -> resourceNameOf(p.getResourceType(), p.getResourceId())));
        return permissions.stream()
                .map(p -> AdminPermissionDto.of(p,
                        principalNames.get(p.getPrincipalId()),
                        resourceNames.get(p.getResourceType().name() + ":" + p.getResourceId())))
                .toList();
    }

    private String principalNameOf(PermissionEntity permission) {
        return switch (permission.getPrincipalType()) {
            case USER -> userRepository.findById(permission.getPrincipalId())
                    .map(UserEntity::getUserName).orElse(null);
            case GROUP -> groupRepository.findById(permission.getPrincipalId())
                    .map(g -> g.getGroupName()).orElse(null);
        };
    }

    private String resourceNameOf(ResourceType type, String id) {
        return switch (type) {
            case DOCUMENT -> documentRepository.findById(id)
                    .map(DocumentEntity::getTitle).orElse(null);
            case FOLDER -> folderRepository.findById(id)
                    .map(FolderEntity::getFolderName).orElse(null);
        };
    }
}
