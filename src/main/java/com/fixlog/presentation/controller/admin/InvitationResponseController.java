package com.fixlog.presentation.controller.admin;

import com.fixlog.application.service.InvitationService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 초대 수락/거절 공개 엔드포인트.
 *
 * <p>토큰을 통해 수락/거절한다. 인증된 사용자만 수락할 수 있다(수락 시 이메일 일치 확인).
 */
@RestController
@RequestMapping("/api/workspaces/invitations")
public class InvitationResponseController {

    private final InvitationService invitationService;

    public InvitationResponseController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/{token}/accept")
    public Response accept(@PathVariable String token) {
        return DataResponse.success(invitationService.accept(token));
    }

    @PostMapping("/{token}/decline")
    public Response decline(@PathVariable String token) {
        return DataResponse.success(invitationService.decline(token));
    }
}
