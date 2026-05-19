package com.fixlog.presentation.controller.auth;

import com.fixlog.common.code.Code;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.common.security.JwtProvider;
import com.fixlog.common.security.SecurityUtil;
import com.fixlog.domain.model.UserEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtProvider jwtProvider;

    public AuthController(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @PostMapping("/token/refresh")
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

    @GetMapping("/session")
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
