package com.fixlog.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 모바일 앱(Custom Tabs) 로그인용 1회용 인가 코드 저장소.
 *
 * <p>딥링크로는 토큰 대신 이 코드만 넘기고, 앱이 {@code POST /auth/exchange}로 코드를 토큰과 교환한다.
 * 토큰이 URL에 실리지 않으므로 브라우저 히스토리·Referer로 새지 않는다.
 *
 * <p>코드는 짧은 TTL 동안만 살아 있고, 한 번 조회되면 성공·실패와 무관하게 즉시 제거된다.
 *
 * <p>저장은 인메모리다. OAuth 인가 과정 자체가 이미 {@code HttpSession}(인메모리)에 의존하므로
 * 서버 인스턴스를 늘리려면 세션과 이 저장소를 함께 외부 저장소(Redis 등)로 옮겨야 한다.
 */
@Component
public class AppAuthCodeStore {

    /** 요구 최소치(128bit)의 두 배. */
    private static final int CODE_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Map<String, IssuedCode> codes = new ConcurrentHashMap<>();
    private final Duration ttl;

    public AppAuthCodeStore(@Value("${oauth2.app-code-ttl-seconds}") long ttlSeconds) {
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /** 코드를 발급해 토큰 쌍을 TTL 동안 보관한다. 반환값은 딥링크로 내보낼 1회용 코드다. */
    public String issue(UUID userId, String accessToken, String refreshToken) {
        purgeExpired();

        byte[] buffer = new byte[CODE_BYTES];
        random.nextBytes(buffer);
        String code = encoder.encodeToString(buffer);

        codes.put(code, new IssuedCode(userId, accessToken, refreshToken, Instant.now().plus(ttl)));
        return code;
    }

    /**
     * 코드를 소비한다. 없거나 이미 만료됐으면 비어 있다.
     * 재사용을 막기 위해 만료된 코드도 조회 시점에 함께 제거한다.
     */
    public Optional<IssuedCode> consume(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }

        IssuedCode issued = codes.remove(code);
        if (issued == null || issued.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(issued);
    }

    /**
     * 교환되지 않은 코드는 소비 시점이 오지 않으므로 발급할 때마다 만료분을 걷어낸다.
     * TTL이 짧아 보관량이 작고, 발급 빈도도 로그인 횟수만큼이라 비용이 문제되지 않는다.
     */
    private void purgeExpired() {
        codes.values().removeIf(IssuedCode::isExpired);
    }

    public record IssuedCode(UUID userId, String accessToken, String refreshToken, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
