package com.fixlog.presentation.controller.auth;

import com.fixlog.common.code.Code;
import com.fixlog.common.response.DataResponse;

public class TokenResponse extends DataResponse<TokenResponse.TokenData> {

    public TokenResponse(String accessToken, String refreshToken) {
        super(Code.SUCCESS, new TokenData(accessToken, refreshToken));
    }

    public record TokenData(String accessToken, String refreshToken) {
    }
}
