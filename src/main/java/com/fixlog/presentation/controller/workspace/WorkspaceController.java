package com.fixlog.presentation.controller.workspace;

import com.fixlog.application.service.WorkspaceService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.domain.model.WorkspaceEntity;
import com.fixlog.domain.model.WorkspaceRole;
import com.fixlog.presentation.dto.request.WorkspaceCreateRequest;
import com.fixlog.presentation.dto.request.WorkspaceInviteRequest;
import com.fixlog.presentation.dto.request.WorkspaceRoleRequest;
import com.fixlog.presentation.dto.response.WorkspaceDto;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping
    public Response create(@RequestBody WorkspaceCreateRequest request) {
        WorkspaceEntity workspace = workspaceService.create(request.workspaceName());
        return DataResponse.success(WorkspaceDto.of(workspace, WorkspaceRole.ADMIN));
    }

    /** 내가 속한 워크스페이스 목록. 여기서 고른 값을 X-Workspace-Id 헤더로 보낸다. */
    @GetMapping
    public Response myWorkspaces() {
        return DataResponse.success(workspaceService.myWorkspaces());
    }

    @GetMapping("/{workspaceId}/members")
    public Response members(@PathVariable UUID workspaceId) {
        return DataResponse.success(workspaceService.members(workspaceId));
    }

    @PostMapping("/{workspaceId}/members")
    public Response invite(@PathVariable UUID workspaceId,
                           @RequestBody WorkspaceInviteRequest request) {
        return DataResponse.success(workspaceService.invite(workspaceId, request.email()));
    }

    @PatchMapping("/{workspaceId}/members/{userId}")
    public Response changeRole(@PathVariable UUID workspaceId,
                               @PathVariable UUID userId,
                               @RequestBody WorkspaceRoleRequest request) {
        return DataResponse.success(workspaceService.changeRole(workspaceId, userId, request.role()));
    }

    @DeleteMapping("/{workspaceId}/members/{userId}")
    public Response removeMember(@PathVariable UUID workspaceId, @PathVariable UUID userId) {
        workspaceService.removeMember(workspaceId, userId);
        return Response.success("구성원을 제거했습니다.");
    }

    @PostMapping("/{workspaceId}/leave")
    public Response leave(@PathVariable UUID workspaceId) {
        workspaceService.leave(workspaceId);
        return Response.success("워크스페이스에서 나왔습니다.");
    }
}
