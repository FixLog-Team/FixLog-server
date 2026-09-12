package com.fixlog.presentation.controller.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@Tag(name = "Auth")
public class LoginController {

    public static final String REDIRECT_URI_SESSION_KEY = "OAUTH2_REDIRECT_URI";

    @GetMapping("/login")
    @Operation(operationId = "login")
    public void login(@RequestParam(value = "redirect_uri", required = false) String redirectUri,
                      HttpServletRequest request,
                      HttpServletResponse response) throws IOException {
        if (redirectUri != null && !redirectUri.isBlank()) {
            request.getSession().setAttribute(REDIRECT_URI_SESSION_KEY, redirectUri);
        }
        response.sendRedirect("/fixlog/oauth2/authorization/google");
    }
}
