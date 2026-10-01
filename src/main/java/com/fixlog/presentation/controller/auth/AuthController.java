package com.fixlog.presentation.controller.auth;

import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.common.security.AppAuthCodeStore;
import com.fixlog.common.security.JwtProvider;
import com.fixlog.common.security.SecurityUtil;
import com.fixlog.domain.model.UserEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth")
public class AuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    private final JwtProvider jwtProvider;
    private final AppAuthCodeStore appAuthCodeStore;

    public AuthController(JwtProvider jwtProvider, AppAuthCodeStore appAuthCodeStore) {
        this.jwtProvider = jwtProvider;
        this.appAuthCodeStore = appAuthCodeStore;
    }

    /**
     * 모바일 앱이 딥링크로 받은 1회용 코드를 토큰 쌍과 교환한다.
     * 코드는 조회 즉시 소비되므로 같은 코드로 다시 요청하면 실패한다.
     */
    @PostMapping("/exchange")
    @Operation(operationId = "exchangeAppCode", summary = "앱 로그인 코드 교환")
    public Response exchange(@RequestBody Map<String, String> body) {
        return appAuthCodeStore.consume(body.get("code"))
                .<Response>map(issued -> {
                    // 코드·토큰은 남기지 않는다. 교환이 누구에게 일어났는지만 추적한다
                    LOGGER.info("앱 로그인 코드 교환 완료: userId={}", issued.userId());
                    return DataResponse.success(Map.of(
                            "accessToken", issued.accessToken(),
                            "refreshToken", issued.refreshToken()
                    ));
                })
                .orElseThrow(() -> new BusinessException(Code.INVALID_REQUEST,
                        "유효하지 않거나 만료된 code입니다."));
    }

    @PostMapping("/token/refresh")
    @Operation(operationId = "refreshToken")
    public Response refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null) {
            return Response.failure(Code.UNAUTHORIZED, "refreshToken이 없습니다.");
        }

        if (!jwtProvider.isValid(refreshToken)) {
            return Response.failure(Code.UNAUTHORIZED, "유효하지 않은 refreshToken입니다.");
        }

        String newAccessToken = jwtProvider.generateAccessToken(jwtProvider.extractUserId(refreshToken));

        return DataResponse.success(Map.of("accessToken", newAccessToken));
    }

    @GetMapping("/token")
    @Operation(operationId = "getAuthSession")
    public Response session() {
        UserEntity user = SecurityUtil.getCurrentUser();
        if (user == null) {
            return Response.failure(Code.UNAUTHORIZED, "인증 정보가 없습니다.");
        }

        return DataResponse.success(Map.of(
                "userId", user.getUserId(),
                "userName", user.getUserName(),
                "email", user.getEmail()
        ));
    }
}
