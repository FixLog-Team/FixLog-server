package com.fixlog.common.security;

import com.fixlog.application.repository.UserOauthRepository;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.presentation.controller.auth.LoginSwagController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final String SWAG_REDIRECT_URL = "http://localhost:8080/fixlog/login/swag/callback";

    private final JwtProvider jwtProvider;
    private final UserOauthRepository userOauthRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final String successRedirectUrl;

    public OAuth2SuccessHandler(
            JwtProvider jwtProvider,
            UserOauthRepository userOauthRepository,
            OAuth2AuthorizedClientService authorizedClientService,
            @Value("${oauth2.success-redirect-url}") String successRedirectUrl
    ) {
        this.jwtProvider = jwtProvider;
        this.userOauthRepository = userOauthRepository;
        this.authorizedClientService = authorizedClientService;
        this.successRedirectUrl = successRedirectUrl;
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

        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getName()
        );
        String googleAccessToken = authorizedClient.getAccessToken().getTokenValue();

        HttpSession session = request.getSession(false);
        boolean isSwagLogin = session != null
                && Boolean.TRUE.equals(session.getAttribute(LoginSwagController.SWAG_LOGIN_FLAG));

        if (isSwagLogin) {
            session.removeAttribute(LoginSwagController.SWAG_LOGIN_FLAG);
        }

        String baseUrl = isSwagLogin ? SWAG_REDIRECT_URL : successRedirectUrl;

        String redirectUrl = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .queryParam("googleAccessToken", googleAccessToken)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
