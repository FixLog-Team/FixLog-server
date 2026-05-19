package com.fixlog.presentation.controller.auth;

import com.fixlog.application.repository.RefreshTokenRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.response.Response;
import com.fixlog.common.security.JwtProvider;
import com.fixlog.domain.model.RefreshTokenEntity;
import com.fixlog.domain.model.UserEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthController(JwtProvider jwtProvider, RefreshTokenRepository refreshTokenRepository) {
        this.jwtProvider = jwtProvider;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @PostMapping("/token/refresh")
    @Transactional
    public Response refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null) {
            return Response.failure(Code.UNAUTHORIZED, "refreshToken이 없습니다.");
        }

        RefreshTokenEntity tokenEntity = refreshTokenRepository.findByToken(refreshToken)
                .orElse(null);

        if (tokenEntity == null || tokenEntity.isExpired()) {
            return Response.failure(Code.UNAUTHORIZED, "유효하지 않은 refreshToken입니다.");
        }

        UserEntity user = tokenEntity.getUser();
        String newAccessToken = jwtProvider.generateAccessToken(user.getUserId());
        String newRefreshToken = jwtProvider.generateRefreshToken(user.getUserId());

        refreshTokenRepository.delete(tokenEntity);
        refreshTokenRepository.save(new RefreshTokenEntity(
                user,
                newRefreshToken,
                Instant.now().plusMillis(jwtProvider.getRefreshTokenExpiry())
        ));

        return new TokenResponse(newAccessToken, newRefreshToken);
    }

    @PostMapping("/logout")
    @Transactional
    public Response logout(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null) {
            return Response.failure(Code.UNAUTHORIZED, "refreshToken이 없습니다.");
        }

        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(refreshTokenRepository::delete);

        return Response.success();
    }
}
