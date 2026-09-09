package com.fixlog.presentation.controller.admin;

import com.fixlog.application.service.AdminConsoleService;
import com.fixlog.application.service.EffectivePermissionService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.domain.model.AuditAction;
import com.fixlog.domain.model.AuditResult;
import com.fixlog.domain.model.ResourceType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * 워크스페이스 관리자 콘솔.
 *
 * <p>경로에 워크스페이스가 항상 들어간다. 관할 없는 전역 조회를 만들지 않기 위한 것이다 (D2).
 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/admin")
public class AdminConsoleController {

    private final AdminConsoleService adminConsoleService;
    private final EffectivePermissionService effectivePermissionService;

    public AdminConsoleController(AdminConsoleService adminConsoleService,
                                  EffectivePermissionService effectivePermissionService) {
        this.adminConsoleService = adminConsoleService;
        this.effectivePermissionService = effectivePermissionService;
    }

    @GetMapping("/permissions")
    public Response permissions(@PathVariable UUID workspaceId) {
        return DataResponse.success(adminConsoleService.permissions(workspaceId));
    }

    @GetMapping("/shares")
    public Response shares(@PathVariable UUID workspaceId) {
        return DataResponse.success(adminConsoleService.shares(workspaceId));
    }

    @GetMapping("/audit-logs")
    public Response auditLogs(@PathVariable UUID workspaceId,
                              @RequestParam(required = false) UUID actorUserId,
                              @RequestParam(required = false) UUID targetUserId,
                              @RequestParam(required = false) AuditAction action,
                              @RequestParam(required = false) AuditResult result,
                              @RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                              @RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return DataResponse.success(
                adminConsoleService.auditLogs(workspaceId, actorUserId, targetUserId, action, result, from, to));
    }

    @GetMapping("/stats")
    public Response stats(@PathVariable UUID workspaceId) {
        return DataResponse.success(adminConsoleService.stats(workspaceId));
    }

    @GetMapping("/users")
    public Response listUsers(@PathVariable UUID workspaceId) {
        return DataResponse.success(adminConsoleService.listUsers(workspaceId));
    }

    @GetMapping("/users/{userId}")
    public Response getUser(@PathVariable UUID workspaceId, @PathVariable UUID userId) {
        return DataResponse.success(adminConsoleService.getUser(workspaceId, userId));
    }

    @GetMapping("/users/{userId}/access")
    public Response userAccess(@PathVariable UUID workspaceId, @PathVariable UUID userId) {
        return DataResponse.success(effectivePermissionService.computeForUser(workspaceId, userId));
    }

    @GetMapping("/permissions/effective")
    public Response effectivePermission(@PathVariable UUID workspaceId,
                                        @RequestParam UUID userId,
                                        @RequestParam ResourceType resourceType,
                                        @RequestParam String resourceId) {
        return DataResponse.success(
                effectivePermissionService.computeSingle(workspaceId, userId, resourceType, resourceId));
    }
}
