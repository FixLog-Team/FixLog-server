package com.fixlog.presentation.controller.admin;

import com.fixlog.application.service.InvitationService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.presentation.dto.request.InvitationRequest;
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

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/admin/invitations")
@Tag(name = "Admin")
public class AdminInvitationController {

    private final InvitationService invitationService;

    public AdminInvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping
    @Operation(operationId = "adminListInvitations")
    public Response list(@PathVariable UUID workspaceId) {
        return DataResponse.success(invitationService.listByWorkspace(workspaceId));
    }

    @PostMapping
    @Operation(operationId = "adminInvite")
    public Response invite(@PathVariable UUID workspaceId,
                           @RequestBody InvitationRequest request) {
        return DataResponse.success(
                invitationService.invite(workspaceId, request.email(), request.resolvedRole()));
    }

    @DeleteMapping("/{invitationId}")
    @Operation(operationId = "adminCancelInvitation")
    public Response cancel(@PathVariable UUID workspaceId,
                           @PathVariable UUID invitationId) {
        invitationService.cancel(workspaceId, invitationId);
        return Response.success("초대를 취소했습니다.");
    }
}
