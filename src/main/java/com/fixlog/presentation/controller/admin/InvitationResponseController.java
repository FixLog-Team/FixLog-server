package com.fixlog.presentation.controller.admin;

import com.fixlog.application.service.InvitationService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 초대 토큰 기반 공개 엔드포인트.
 *
 * <p>미리보기·거절은 인증 불필요. 수락은 인증 필요(로그인 이메일 == 초대 이메일).
 */
@RestController
@RequestMapping("/api/workspaces/invitations")
@Tag(name = "Invitation")
public class InvitationResponseController {

    private final InvitationService invitationService;

    public InvitationResponseController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping("/{token}")
    @Operation(operationId = "getInvitationPreview")
    public Response getPreview(@PathVariable String token) {
        return DataResponse.success(invitationService.getPreview(token));
    }

    @PostMapping("/{token}/accept")
    @Operation(operationId = "acceptInvitation")
    public Response accept(@PathVariable String token) {
        return DataResponse.success(invitationService.accept(token));
    }

    @PostMapping("/{token}/decline")
    @Operation(operationId = "declineInvitation")
    public Response decline(@PathVariable String token) {
        return DataResponse.success(invitationService.decline(token));
    }
}
