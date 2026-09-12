package com.fixlog.presentation.dto.request;

public record ApiKeyRequest(
        String provider,
        String apiKey
) {
    /** 요청 본문이 로그에 실려도 키가 새지 않도록 한다. */
    @Override
    public String toString() {
        return "ApiKeyRequest{provider='" + provider + "'}";
    }
}
