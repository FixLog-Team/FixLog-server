package com.fixlog.common.exception;

import com.fixlog.common.code.Code;

public class BusinessException extends RuntimeException {
    private final Code code;

    public BusinessException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
