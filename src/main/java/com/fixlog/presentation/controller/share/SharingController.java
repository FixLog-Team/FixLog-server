package com.fixlog.presentation.controller.share;

import com.fixlog.application.service.DocumentService;
import com.fixlog.application.service.PermissionEvaluator;
import com.fixlog.application.service.PermissionService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.domain.model.PermissionEntity;
import com.fixlog.domain.model.PrincipalType;
import com.fixlog.domain.model.ResourceType;
import com.fixlog.presentation.dto.request.ShareRequest;
import com.fixlog.presentation.dto.response.DocumentDto;
import com.fixlog.presentation.dto.response.MyPermissionDto;
import com.fixlog.presentation.dto.response.PermissionDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 공유 API. 공유는 권한 레코드를 만드는 행위이므로 폴더와 문서가 같은 규칙을 쓴다 (FR-SHR-001~005).
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Share")
public class SharingController {

    private final PermissionService permissionService;
    private final DocumentService documentService;
    private final PermissionEvaluator permissionEvaluator;

    public SharingController(PermissionService permissionService, DocumentService documentService,
                             PermissionEvaluator permissionEvaluator) {
        this.permissionService = permissionService;
        this.documentService = documentService;
        this.permissionEvaluator = permissionEvaluator;
    }

    /** 현재 유저가 이 문서에 대해 갖는 유효 권한 + 출처. 프론트 UI 제어(다운로드 버튼 등)에 사용한다. */
    @GetMapping("/documents/{documentId}/my-permission")
    @Operation(operationId = "myDocumentPermission")
    public Response myDocumentPermission(@PathVariable String documentId) {
        return DataResponse.success(
                MyPermissionDto.of(permissionEvaluator.evaluateWithSource(ResourceType.DOCUMENT, documentId)));
    }

    /** 현재 유저가 이 폴더에 대해 갖는 유효 권한 + 출처. */
    @GetMapping("/folders/{folderId}/my-permission")
    @Operation(operationId = "myFolderPermission")
    public Response myFolderPermission(@PathVariable String folderId) {
        return DataResponse.success(
                MyPermissionDto.of(permissionEvaluator.evaluateWithSource(ResourceType.FOLDER, folderId)));
    }

    /** 내가 만들지 않았지만 권한을 받은 문서 (FR-SHR-005). {@code /{documentId}}보다 먼저 선언한다. */
    @GetMapping("/documents/shared-with-me")
    @Operation(operationId = "sharedWithMe")
    public Response sharedWithMe() {
        return DataResponse.success(documentService.sharedWithMe().stream().map(DocumentDto::from).toList());
    }

    @GetMapping("/documents/{documentId}/permissions")
    @Operation(operationId = "listDocumentPermissions")
    public Response documentPermissions(@PathVariable String documentId) {
        return DataResponse.success(permissionService.listFor(ResourceType.DOCUMENT, documentId));
    }

    @PostMapping("/documents/{documentId}/permissions")
    @Operation(operationId = "shareDocument")
    public Response shareDocument(@PathVariable String documentId, @RequestBody ShareRequest request) {
        return DataResponse.success(share(ResourceType.DOCUMENT, documentId, request));
    }

    @DeleteMapping("/documents/{documentId}/permissions/{permissionId}")
    @Operation(operationId = "revokeDocumentPermission")
    public Response revokeDocument(@PathVariable String documentId, @PathVariable UUID permissionId) {
        permissionService.revoke(ResourceType.DOCUMENT, documentId, permissionId);
        return Response.success("공유를 회수했습니다.");
    }

    @GetMapping("/folders/{folderId}/permissions")
    @Operation(operationId = "listFolderPermissions")
    public Response folderPermissions(@PathVariable String folderId) {
        return DataResponse.success(permissionService.listFor(ResourceType.FOLDER, folderId));
    }

    @PostMapping("/folders/{folderId}/permissions")
    @Operation(operationId = "shareFolder")
    public Response shareFolder(@PathVariable String folderId, @RequestBody ShareRequest request) {
        return DataResponse.success(share(ResourceType.FOLDER, folderId, request));
    }

    @DeleteMapping("/folders/{folderId}/permissions/{permissionId}")
    @Operation(operationId = "revokeFolderPermission")
    public Response revokeFolder(@PathVariable String folderId, @PathVariable UUID permissionId) {
        permissionService.revoke(ResourceType.FOLDER, folderId, permissionId);
        return Response.success("공유를 회수했습니다.");
    }

    private PermissionDto share(ResourceType resourceType, String resourceId, ShareRequest request) {
        PrincipalType principalType =
                request.principalType() == null ? PrincipalType.USER : request.principalType();

        PermissionEntity saved = request.principalId() != null
                ? permissionService.share(resourceType, resourceId, principalType,
                        request.principalId(), request.resolvedPermissionType(), request.allowsDownload())
                : permissionService.shareWithEmail(resourceType, resourceId,
                        request.email(), request.resolvedPermissionType(), request.allowsDownload());

        return PermissionDto.of(saved, null);
    }
}
