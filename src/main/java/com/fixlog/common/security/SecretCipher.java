package com.fixlog.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 저장용 대칭 암호화. 사용자 API Key처럼 원문이 필요하지만 노출되면 안 되는 값에 쓴다.
 *
 * <p>GCM을 쓰는 이유는 기밀성뿐 아니라 변조 여부까지 확인하기 위함이다. 매 암호화마다 새 IV를
 * 만들어 앞에 붙인다 — 같은 키를 같은 IV로 재사용하면 GCM은 안전성을 잃는다.
 */
@Component
public class SecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${fixlog.encryption.key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            this.key = null;
            return;
        }
        byte[] decoded = Base64.getDecoder().decode(base64Key);
        if (decoded.length != 16 && decoded.length != 24 && decoded.length != 32) {
            throw new IllegalStateException(
                    "fixlog.encryption.key는 base64로 인코딩된 16/24/32바이트 키여야 합니다.");
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public String encrypt(String plainText) {
        requireConfigured();
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            // 예외 메시지에 원문이 실리지 않도록 원인을 그대로 넘기지 않는다.
            throw new IllegalStateException("암호화에 실패했습니다.");
        }
    }

    public String decrypt(String encrypted) {
        requireConfigured();
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("복호화에 실패했습니다.");
        }
    }

    private void requireConfigured() {
        if (key == null) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY가 설정되지 않아 사용자 API Key 기능을 사용할 수 없습니다.");
        }
    }
}
