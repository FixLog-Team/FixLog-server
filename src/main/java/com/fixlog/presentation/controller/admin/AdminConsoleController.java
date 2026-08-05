package com.fixlog.presentation.controller.admin;

import com.fixlog.application.service.AdminConsoleService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.domain.model.AuditAction;
import com.fixlog.domain.model.AuditResult;
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

    public AdminConsoleController(AdminConsoleService adminConsoleService) {
        this.adminConsoleService = adminConsoleService;
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
                              @RequestParam(required = false) AuditAction action,
                              @RequestParam(required = false) AuditResult result,
                              @RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                              @RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return DataResponse.success(
                adminConsoleService.auditLogs(workspaceId, actorUserId, action, result, from, to));
    }

    @GetMapping("/stats")
    public Response stats(@PathVariable UUID workspaceId) {
        return DataResponse.success(adminConsoleService.stats(workspaceId));
    }
}
