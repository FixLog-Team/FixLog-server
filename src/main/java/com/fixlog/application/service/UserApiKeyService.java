package com.fixlog.application.service;

import com.fixlog.application.repository.UserApiKeyRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.security.SecretCipher;
import com.fixlog.domain.model.UserApiKeyEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 사용자 API Key 보관 (FR-AI-002, 003).
 *
 * <p>원문을 돌려주는 공개 메서드를 두지 않는다. 복호화는 호출 직전에 자격증명을 구성하는
 * 경로에서만 쓰이며, 그 값은 응답이나 로그로 나가지 않는다.
 */
@Service
public class UserApiKeyService {

    private static final int HINT_LENGTH = 4;

    private final UserApiKeyRepository apiKeyRepository;
    private final SecretCipher secretCipher;
    private final WorkspaceContext workspaceContext;

    public UserApiKeyService(UserApiKeyRepository apiKeyRepository,
                             SecretCipher secretCipher,
                             WorkspaceContext workspaceContext) {
        this.apiKeyRepository = apiKeyRepository;
        this.secretCipher = secretCipher;
        this.workspaceContext = workspaceContext;
    }

    /** 등록·교체를 한 경로로 처리한다. 같은 제공자에 키가 둘 있으면 어느 것을 쓸지 모호해진다. */
    @Transactional
    public UserApiKeyEntity register(String provider, String apiKey) {
        UUID userId = workspaceContext.requireCurrentUserId();
        String normalizedProvider = requireProvider(provider);
        String key = requireKey(apiKey);

        String encrypted = secretCipher.encrypt(key);
        String hint = hintOf(key);

        return apiKeyRepository.findByUserIdAndProvider(userId, normalizedProvider)
                .map(existing -> {
                    existing.replace(encrypted, hint);
                    return apiKeyRepository.save(existing);
                })
                .orElseGet(() -> apiKeyRepository.save(
                        new UserApiKeyEntity(userId, normalizedProvider, encrypted, hint)));
    }

    @Transactional(readOnly = true)
    public List<UserApiKeyEntity> myKeys() {
        return apiKeyRepository.findByUserId(workspaceContext.requireCurrentUserId());
    }

    @Transactional
    public void delete(UUID keyId) {
        UUID userId = workspaceContext.requireCurrentUserId();
        UserApiKeyEntity key = apiKeyRepository.findById(keyId)
                .filter(k -> k.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "등록된 키를 찾을 수 없습니다."));
        apiKeyRepository.delete(key);
    }

    /**
     * 호출 직전 자격증명을 구성하기 위한 경로. 패키지 밖으로 원문을 흘리지 않도록
     * 호출부는 같은 애플리케이션 계층으로 한정한다.
     */
    @Transactional(readOnly = true)
    public Optional<String> resolveKey(UUID userId, String provider) {
        return apiKeyRepository.findByUserIdAndProvider(userId, requireProvider(provider))
                .map(key -> secretCipher.decrypt(key.getEncryptedKey()));
    }

    private String requireProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new BusinessException(Code.INVALID_REQUEST, "제공자는 필수입니다.");
        }
        return provider.trim().toLowerCase();
    }

    private String requireKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(Code.INVALID_REQUEST, "API Key는 필수입니다.");
        }
        return apiKey.trim();
    }

    private String hintOf(String key) {
        return key.length() <= HINT_LENGTH ? key : key.substring(key.length() - HINT_LENGTH);
    }
}
