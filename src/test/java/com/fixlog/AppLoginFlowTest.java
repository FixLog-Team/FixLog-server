package com.fixlog;

import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.common.security.AppAuthCodeStore;
import com.fixlog.common.security.AppLoginFlow;
import com.fixlog.common.security.JwtProvider;
import com.fixlog.common.security.OAuth2FailureHandler;
import com.fixlog.common.security.OAuth2SuccessHandler;
import com.fixlog.application.repository.UserOauthRepository;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.UserOauthEntity;
import com.fixlog.presentation.controller.auth.AuthController;
import com.fixlog.presentation.controller.auth.LoginAppController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 모바일 앱(Android Custom Tabs) 로그인 플로우.
 *
 * <p>딥링크에는 토큰 대신 1회용 코드만 실려야 하고, 그 코드는 한 번만·짧은 시간만 통해야 한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppLoginFlowTest {

    private static final String ALLOWED_REDIRECT_URI = "kr.co.fixlog://oauth2callback";
    private static final String STATE = "nonce-123";

    @Mock
    private UserOauthRepository userOauthRepository;

    @Mock
    private OAuth2AuthorizedClientService authorizedClientService;

    private AppAuthCodeStore codeStore;
    private JwtProvider jwtProvider;
    private LoginAppController loginAppController;
    private AuthController authController;
    private OAuth2FailureHandler failureHandler;
    private UserEntity user;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        codeStore = new AppAuthCodeStore(60);
        jwtProvider = new JwtProvider("test-secret-key-for-testing-only-must-be-long-enough", 3600000, 1209600000);
        loginAppController = new LoginAppController(List.of(ALLOWED_REDIRECT_URI));
        authController = new AuthController(jwtProvider, codeStore);
        failureHandler = new OAuth2FailureHandler();

        userId = UUID.randomUUID();
        user = new UserEntity("테스터", "tester@fixlog.dev");
        Field userIdField = UserEntity.class.getDeclaredField("userId");
        userIdField.setAccessible(true);
        userIdField.set(user, userId);

        when(userOauthRepository.findByProviderAndProviderId("google", "sub-123"))
                .thenReturn(Optional.of(new UserOauthEntity(user, "google", "sub-123")));
    }

    // --- GET /login/app ---

    @Test
    void 허용된_redirect_uri면_세션에_담고_구글_인가로_보낸다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        loginAppController.loginApp(ALLOWED_REDIRECT_URI, STATE, request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("/fixlog/oauth2/authorization/google");
        assertThat(request.getSession().getAttribute(AppLoginFlow.REDIRECT_URI_SESSION_KEY))
                .isEqualTo(ALLOWED_REDIRECT_URI);
        assertThat(request.getSession().getAttribute(AppLoginFlow.STATE_SESSION_KEY)).isEqualTo(STATE);
    }

    @Test
    void 화이트리스트에_없는_redirect_uri는_거부된다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() ->
                loginAppController.loginApp("https://evil.example.com/steal", STATE, request, response))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(Code.INVALID_REQUEST);
    }

    @Test
    void redirect_uri가_없으면_거부된다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> loginAppController.loginApp(null, STATE, request, response))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(Code.INVALID_REQUEST);
    }

    // --- 로그인 성공 후 딥링크 복귀 ---

    @Test
    void 앱_로그인_성공은_토큰_대신_코드만_딥링크로_내보낸다() throws Exception {
        MockHttpServletRequest request = appLoginRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler().onAuthenticationSuccess(request, response, googleAuthentication());

        String location = response.getHeader("Location");
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(location).startsWith(ALLOWED_REDIRECT_URI + "?code=");
        assertThat(location).contains("state=" + STATE);
        assertThat(location).doesNotContain("accessToken").doesNotContain("refreshToken");
    }

    @Test
    void 앱_로그인_상태는_한_번만_소비된다() throws Exception {
        MockHttpServletRequest request = appLoginRequest();
        successHandler().onAuthenticationSuccess(request, new MockHttpServletResponse(), googleAuthentication());

        // 같은 브라우저 세션으로 이어지는 다음 로그인은 웹 로그인으로 처리돼야 한다
        MockHttpServletResponse second = new MockHttpServletResponse();
        successHandler().onAuthenticationSuccess(request, second, googleAuthentication());

        assertThat(second.getRedirectedUrl())
                .startsWith("http://localhost:5173/login/callback")
                .doesNotStartWith(ALLOWED_REDIRECT_URI);
    }

    @Test
    void 웹_로그인_성공은_기존대로_토큰을_쿼리로_내보낸다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler().onAuthenticationSuccess(request, response, googleAuthentication());

        assertThat(response.getRedirectedUrl())
                .startsWith("http://localhost:5173/login/callback")
                .contains("accessToken=")
                .contains("refreshToken=");
    }

    @Test
    void 앱_로그인_실패는_에러를_딥링크로_내보낸다() throws Exception {
        MockHttpServletRequest request = appLoginRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        failureHandler.onAuthenticationFailure(request, response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

        String location = response.getHeader("Location");
        assertThat(location).isEqualTo(ALLOWED_REDIRECT_URI + "?error=access_denied&state=" + STATE);
    }

    @Test
    void 웹_로그인_실패는_기존대로_에러_페이지로_보낸다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        failureHandler.onAuthenticationFailure(request, response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error");
    }

    // --- POST /auth/exchange ---

    @Test
    void 발급된_코드는_토큰_쌍으로_교환된다() {
        String code = codeStore.issue(userId, "access-token", "refresh-token");

        Response response = authController.exchange(Map.of("code", code));

        assertThat(response.getCode()).isEqualTo(Code.SUCCESS);
        @SuppressWarnings("unchecked")
        Map<String, String> result = (Map<String, String>) ((DataResponse<?>) response).getResult();
        assertThat(result).containsEntry("accessToken", "access-token")
                .containsEntry("refreshToken", "refresh-token");
    }

    @Test
    void 이미_사용한_코드는_교환되지_않는다() {
        String code = codeStore.issue(userId, "access-token", "refresh-token");
        authController.exchange(Map.of("code", code));

        assertThatThrownBy(() -> authController.exchange(Map.of("code", code)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(Code.INVALID_REQUEST);
    }

    @Test
    void 만료된_코드는_교환되지_않는다() {
        AppAuthCodeStore expiredStore = new AppAuthCodeStore(-1);
        AuthController controller = new AuthController(jwtProvider, expiredStore);
        String code = expiredStore.issue(userId, "access-token", "refresh-token");

        assertThatThrownBy(() -> controller.exchange(Map.of("code", code)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(Code.INVALID_REQUEST);
    }

    @Test
    void 존재하지_않는_코드는_교환되지_않는다() {
        assertThatThrownBy(() -> authController.exchange(Map.of("code", "not-issued")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void code가_없는_요청은_교환되지_않는다() {
        assertThatThrownBy(() -> authController.exchange(Map.of()))
                .isInstanceOf(BusinessException.class);
    }

    private OAuth2SuccessHandler successHandler() {
        return new OAuth2SuccessHandler(
                jwtProvider,
                userOauthRepository,
                authorizedClientService,
                codeStore,
                "http://localhost:5173/login/callback",
                "http://localhost:8080/fixlog/login/swag/callback",
                List.of("http://localhost:5173")
        );
    }

    private MockHttpServletRequest appLoginRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AppLoginFlow.store(request.getSession(), ALLOWED_REDIRECT_URI, STATE);
        return request;
    }

    private Authentication googleAuthentication() {
        DefaultOAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "sub-123"),
                "sub"
        );
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }
}
