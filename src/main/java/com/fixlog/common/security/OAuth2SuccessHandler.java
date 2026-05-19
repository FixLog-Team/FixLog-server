package com.fixlog.common.security;

import com.fixlog.application.repository.UserOauthRepository;
import com.fixlog.domain.model.UserEntity;
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

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final UserOauthRepository userOauthRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final String successRedirectUrl;
    private final String swagRedirectUrl;

    public OAuth2SuccessHandler(
            JwtProvider jwtProvider,
            UserOauthRepository userOauthRepository,
            OAuth2AuthorizedClientService authorizedClientService,
            @Value("${oauth2.success-redirect-url}") String successRedirectUrl,
            @Value("${oauth2.swag-redirect-url:http://localhost:8080/fixlog/login/swag/callback}") String swagRedirectUrl
    ) {
        this.jwtProvider = jwtProvider;
        this.userOauthRepository = userOauthRepository;
        this.authorizedClientService = authorizedClientService;
        this.successRedirectUrl = successRedirectUrl;
        this.swagRedirectUrl = swagRedirectUrl;
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

        HttpSession session = request.getSession(false);
        boolean isSwagLogin = session != null
                && Boolean.TRUE.equals(session.getAttribute(LoginSwagController.SWAG_LOGIN_FLAG));

        if (isSwagLogin) {
            session.removeAttribute(LoginSwagController.SWAG_LOGIN_FLAG);
        }

        String baseUrl = isSwagLogin ? swagRedirectUrl : successRedirectUrl;

        String redirectUrl = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
