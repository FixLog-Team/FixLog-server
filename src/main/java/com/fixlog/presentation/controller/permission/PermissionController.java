package com.fixlog.presentation.controller.permission;

import com.fixlog.application.service.PermissionAdminService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.domain.model.NodeType;
import com.fixlog.presentation.dto.request.BaseAccessRequest;
import com.fixlog.presentation.dto.request.PermissionOverrideRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 콘텐츠 권한 관리. 권한 변경은 이 경로에서만 가능하다.
 * 모든 엔드포인트는 워크스페이스의 OWNER 또는 ADMIN만 호출할 수 있다.
 *
 * <p>{@code nodeType}은 대문자로 넘긴다: {@code WORKSPACE}, {@code FOLDER}, {@code DOCUMENT}.
 */
@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final PermissionAdminService permissionAdminService;

    public PermissionController(PermissionAdminService permissionAdminService) {
        this.permissionAdminService = permissionAdminService;
    }

    @GetMapping("/nodes/{nodeType}/{nodeId}")
    public Response getNodePermission(@PathVariable NodeType nodeType,
                                      @PathVariable String nodeId) {
        return DataResponse.success(permissionAdminService.getNodePermission(nodeType, nodeId));
    }

    @PatchMapping("/nodes/{nodeType}/{nodeId}/base-access")
    public Response changeBaseAccess(@PathVariable NodeType nodeType,
                                     @PathVariable String nodeId,
                                     @Valid @RequestBody BaseAccessRequest req) {
        return DataResponse.success(
                permissionAdminService.changeBaseAccess(nodeType, nodeId, req.baseAccess()));
    }

    @PutMapping("/nodes/{nodeType}/{nodeId}/overrides/{userId}")
    public Response upsertOverride(@PathVariable NodeType nodeType,
                                   @PathVariable String nodeId,
                                   @PathVariable String userId,
                                   @Valid @RequestBody PermissionOverrideRequest req) {
        return DataResponse.success(
                permissionAdminService.upsertOverride(nodeType, nodeId, userId, req.effect()));
    }

    @DeleteMapping("/nodes/{nodeType}/{nodeId}/overrides/{userId}")
    public Response removeOverride(@PathVariable NodeType nodeType,
                                   @PathVariable String nodeId,
                                   @PathVariable String userId) {
        return DataResponse.success(
                permissionAdminService.removeOverride(nodeType, nodeId, userId));
    }

    /**
     * 한 사용자의 노드별 최종 권한과 출처 조회.
     * 판정은 실제 접근에 쓰이는 것과 같은 경로를 통과하므로 표시와 동작이 어긋나지 않는다.
     */
    @GetMapping("/users/{userId}/access")
    public Response listUserAccess(@PathVariable String userId,
                                   @RequestParam(required = false) String workspaceId,
                                   @RequestParam(required = false) NodeType nodeType,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return DataResponse.success(
                permissionAdminService.listUserAccess(userId, workspaceId, nodeType, page, size));
    }
}
