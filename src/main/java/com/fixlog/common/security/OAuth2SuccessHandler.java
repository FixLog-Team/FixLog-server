package com.fixlog.common.security;

import com.fixlog.application.repository.UserOauthRepository;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.presentation.controller.auth.LoginController;
import com.fixlog.presentation.controller.auth.LoginSwagController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.List;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final UserOauthRepository userOauthRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final String defaultRedirectUrl;
    private final String swagRedirectUrl;
    private final List<String> allowedOrigins;

    public OAuth2SuccessHandler(
            JwtProvider jwtProvider,
            UserOauthRepository userOauthRepository,
            OAuth2AuthorizedClientService authorizedClientService,
            @Value("${oauth2.success-redirect-url}") String defaultRedirectUrl,
            @Value("${oauth2.swag-redirect-url:http://localhost:8080/fixlog/login/swag/callback}") String swagRedirectUrl,
            @Value("#{'${cors.allowed-origins}'.split(',')}") List<String> allowedOrigins
    ) {
        this.jwtProvider = jwtProvider;
        this.userOauthRepository = userOauthRepository;
        this.authorizedClientService = authorizedClientService;
        this.defaultRedirectUrl = defaultRedirectUrl;
        this.swagRedirectUrl = swagRedirectUrl;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = oauthToken.getPrincipal();
        String providerId = oAuth2User.getAttribute("sub");

        UserEntity user = userOauthRepository.findByProviderAndProviderId("google", providerId)
                .orElseThrow(() -> new IllegalStateException("OAuth 사용자 정보를 찾을 수 없습니다."))
                .getUser();

        String accessToken = jwtProvider.generateAccessToken(user.getUserId());
        String refreshToken = jwtProvider.generateRefreshToken(user.getUserId());

        String baseUrl = resolveRedirectUrl(request);

        String redirectUrl = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private String resolveRedirectUrl(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return defaultRedirectUrl;
        }

        boolean isSwagLogin = Boolean.TRUE.equals(session.getAttribute(LoginSwagController.SWAG_LOGIN_FLAG));
        if (isSwagLogin) {
            session.removeAttribute(LoginSwagController.SWAG_LOGIN_FLAG);
            return swagRedirectUrl;
        }

        String redirectUri = (String) session.getAttribute(LoginController.REDIRECT_URI_SESSION_KEY);
        session.removeAttribute(LoginController.REDIRECT_URI_SESSION_KEY);

        if (redirectUri != null && isAllowedOrigin(redirectUri)) {
            return redirectUri;
        }

        return defaultRedirectUrl;
    }

    private boolean isAllowedOrigin(String url) {
        try {
            URI uri = URI.create(url);
            String origin = uri.getScheme() + "://" + uri.getAuthority();
            return allowedOrigins.contains(origin);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
