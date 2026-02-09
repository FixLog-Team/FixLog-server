package com.fixlog.presentation.controller.login;


import com.fixlog.common.response.Response;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {

    @GetMapping("/main")
    public Response main(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return Response.failure("인증 정보가 없습니다.");
        }
        String name = principal.getAttribute("name");

        return Response.success();
    }
}