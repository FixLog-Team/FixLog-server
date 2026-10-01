package com.fixlog.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 모바일 앱(Custom Tabs) 로그인 플로우의 왕복 상태와 복귀 딥링크를 다룬다.
 *
 * <p>{@code GET /login/app}이 받은 {@code redirect_uri}·{@code state}를 세션에 맡겨 두고,
 * Google 인가가 끝난 뒤 성공·실패 핸들러가 다시 꺼내 앱 딥링크로 돌려보낸다.
 * 웹 로그인과 같은 핸들러를 공유하므로, 꺼낼 때 세션에서 지워 다음 로그인에 새지 않게 한다.
 */
public final class AppLoginFlow {

    public static final String REDIRECT_URI_SESSION_KEY = "APP_OAUTH2_REDIRECT_URI";
    public static final String STATE_SESSION_KEY = "APP_OAUTH2_STATE";

    private AppLoginFlow() {
    }

    public static void store(HttpSession session, String redirectUri, String state) {
        session.setAttribute(REDIRECT_URI_SESSION_KEY, redirectUri);
        session.setAttribute(STATE_SESSION_KEY, state);
    }

    /** 앱 로그인으로 시작된 요청이면 보관해 둔 값을 꺼내 세션에서 지운다. 아니면 {@code null}. */
    public static AppLogin consume(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }

        String redirectUri = (String) session.getAttribute(REDIRECT_URI_SESSION_KEY);
        if (redirectUri == null) {
            return null;
        }

        String state = (String) session.getAttribute(STATE_SESSION_KEY);
        session.removeAttribute(REDIRECT_URI_SESSION_KEY);
        session.removeAttribute(STATE_SESSION_KEY);

        return new AppLogin(redirectUri, state);
    }

    /** 1회용 코드만 실어 앱으로 돌려보낸다. 토큰은 {@code POST /auth/exchange}에서 교환한다. */
    public static void sendCode(HttpServletResponse response, AppLogin appLogin, String code) {
        sendDeepLink(response, appLogin, "code", code);
    }

    public static void sendError(HttpServletResponse response, AppLogin appLogin, String error) {
        sendDeepLink(response, appLogin, "error", error);
    }

    private static void sendDeepLink(HttpServletResponse response, AppLogin appLogin, String name, String value) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(appLogin.redirectUri())
                .queryParam(name, value);
        if (appLogin.state() != null) {
            builder.queryParam("state", appLogin.state());
        }

        // 커스텀 스킴에는 sendRedirect가 jsessionid를 덧붙일 수 있어 Location 헤더를 직접 쓴다
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader(HttpHeaders.LOCATION, builder.encode().build().toUriString());
    }

    public record AppLogin(String redirectUri, String state) {
    }
}
