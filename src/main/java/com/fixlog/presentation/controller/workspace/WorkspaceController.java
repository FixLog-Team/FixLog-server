package com.fixlog.presentation.controller.workspace;

import com.fixlog.application.service.WorkspaceService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping("/api/users/me/workspaces")
    public Response myWorkspaces() {
        return DataResponse.success(workspaceService.myWorkspaces());
    }

    @GetMapping("/api/workspaces/{workspaceId}/members")
    public Response members(@PathVariable String workspaceId) {
        return DataResponse.success(workspaceService.members(workspaceId));
    }
}
