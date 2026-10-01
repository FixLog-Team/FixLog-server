package com.fixlog.common.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 로그인 실패 처리.
 *
 * <p>모바일 앱 로그인은 브라우저에 에러 페이지를 남기는 대신 딥링크로 실패를 알려야 앱이 화면을 정리할 수 있다.
 * 웹 로그인은 Spring Security 기본 동작({@code /login?error} 리다이렉트)을 그대로 둔다.
 */
@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final String DEFAULT_ERROR_CODE = "login_failed";

    public OAuth2FailureHandler() {
        super("/login?error");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        AppLoginFlow.AppLogin appLogin = AppLoginFlow.consume(request);
        if (appLogin == null) {
            super.onAuthenticationFailure(request, response, exception);
            return;
        }

        AppLoginFlow.sendError(response, appLogin, resolveErrorCode(exception));
    }

    private String resolveErrorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauthException) {
            String errorCode = oauthException.getError().getErrorCode();
            if (errorCode != null && !errorCode.isBlank()) {
                return errorCode;
            }
        }
        return DEFAULT_ERROR_CODE;
    }
}
