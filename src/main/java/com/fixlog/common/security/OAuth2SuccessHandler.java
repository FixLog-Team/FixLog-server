package com.fixlog.common.security;

import com.fixlog.application.repository.RefreshTokenRepository;
import com.fixlog.application.repository.UserOauthRepository;
import com.fixlog.domain.model.RefreshTokenEntity;
import com.fixlog.domain.model.UserEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Instant;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final UserOauthRepository userOauthRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final String successRedirectUrl;

    public OAuth2SuccessHandler(
            JwtProvider jwtProvider,
            UserOauthRepository userOauthRepository,
            RefreshTokenRepository refreshTokenRepository,
            @Value("${oauth2.success-redirect-url}") String successRedirectUrl
    ) {
        this.jwtProvider = jwtProvider;
        this.userOauthRepository = userOauthRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.successRedirectUrl = successRedirectUrl;
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String providerId = oAuth2User.getAttribute("sub");

        UserEntity user = userOauthRepository.findByProviderAndProviderId("google", providerId)
                .orElseThrow(() -> new IllegalStateException("OAuth 사용자 정보를 찾을 수 없습니다."))
                .getUser();

        String accessToken = jwtProvider.generateAccessToken(user.getUserId());
        String refreshToken = jwtProvider.generateRefreshToken(user.getUserId());

        refreshTokenRepository.deleteByUser(user);
        refreshTokenRepository.save(new RefreshTokenEntity(
                user,
                refreshToken,
                Instant.now().plusMillis(jwtProvider.getRefreshTokenExpiry())
        ));

        String redirectUrl = UriComponentsBuilder.fromUriString(successRedirectUrl)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
