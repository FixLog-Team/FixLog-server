package com.fixlog.presentation.controller.auth;

import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.security.AppLoginFlow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * 모바일 앱(Android Custom Tabs) 로그인 시작점.
 *
 * <p>앱은 시스템 브라우저로 이 엔드포인트를 열고, 로그인이 끝나면 등록된 딥링크로 1회용 코드를 받는다.
 * WebView 로그인은 Google 정책상 차단되므로({@code disallowed_useragent}) 이 경로를 쓴다.
 */
@RestController
@Tag(name = "Auth")
public class LoginAppController {

    private final List<String> allowedRedirectUris;

    public LoginAppController(
            @Value("#{'${oauth2.app-redirect-uris}'.split(',')}") List<String> allowedRedirectUris
    ) {
        this.allowedRedirectUris = allowedRedirectUris.stream().map(String::trim).toList();
    }

    @GetMapping("/login/app")
    @Operation(operationId = "loginApp", summary = "모바일 앱 로그인 시작 (Custom Tabs)")
    public void loginApp(@RequestParam(value = "redirect_uri", required = false) String redirectUri,
                         @RequestParam(value = "state", required = false) String state,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        // 화이트리스트에 없는 주소로 코드를 흘리면 계정이 탈취되므로 정확히 일치할 때만 통과시킨다
        if (redirectUri == null || !allowedRedirectUris.contains(redirectUri)) {
            throw new BusinessException(Code.INVALID_REQUEST, "허용되지 않은 redirect_uri입니다.");
        }

        AppLoginFlow.store(request.getSession(), redirectUri, state);
        response.sendRedirect("/fixlog/oauth2/authorization/google");
    }
}
