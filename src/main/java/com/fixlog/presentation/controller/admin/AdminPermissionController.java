package com.fixlog.presentation.controller.admin;

import com.fixlog.application.service.AdminPermissionService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.domain.model.ResourceType;
import com.fixlog.presentation.dto.request.AdminGrantPermissionRequest;
import com.fixlog.presentation.dto.request.AdminUpdatePermissionRequest;
import com.fixlog.presentation.dto.request.FolderSettingsRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin 전용 권한 관리 API.
 * 경로에 워크스페이스가 항상 포함되어 범위를 제한한다.
 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/admin")
public class AdminPermissionController {

    private final AdminPermissionService adminPermissionService;

    public AdminPermissionController(AdminPermissionService adminPermissionService) {
        this.adminPermissionService = adminPermissionService;
    }

    @GetMapping("/permissions/resources/{resourceType}/{resourceId}")
    public Response listForResource(@PathVariable UUID workspaceId,
                                    @PathVariable ResourceType resourceType,
                                    @PathVariable String resourceId) {
        return DataResponse.success(
                adminPermissionService.listForResource(workspaceId, resourceType, resourceId));
    }

    @PatchMapping("/permissions/resources/folders/{folderId}/settings")
    public Response updateFolderSettings(@PathVariable UUID workspaceId,
                                         @PathVariable String folderId,
                                         @RequestBody FolderSettingsRequest request) {
        adminPermissionService.updateFolderSettings(
                workspaceId, folderId,
                request.resolvedInheritFromParent(), request.resolvedBaseAccess());
        return Response.success("폴더 설정을 변경했습니다.");
    }

    @PostMapping("/permissions")
    public Response grant(@PathVariable UUID workspaceId,
                          @RequestBody AdminGrantPermissionRequest request) {
        return DataResponse.success(adminPermissionService.grant(
                workspaceId,
                request.resourceType(), request.resourceId(),
                request.principalType(), request.principalId(),
                request.resolvedPermissionType(), request.resolvedCanDownload()));
    }

    @PutMapping("/permissions/{permissionId}")
    public Response update(@PathVariable UUID workspaceId,
                           @PathVariable UUID permissionId,
                           @RequestBody AdminUpdatePermissionRequest request) {
        return DataResponse.success(adminPermissionService.updatePermission(
                workspaceId, permissionId,
                request.resolvedPermissionType(), request.resolvedCanDownload()));
    }

    @DeleteMapping("/permissions/{permissionId}")
    public Response delete(@PathVariable UUID workspaceId,
                           @PathVariable UUID permissionId) {
        adminPermissionService.deletePermission(workspaceId, permissionId);
        return Response.success("권한을 삭제했습니다.");
    }
}
