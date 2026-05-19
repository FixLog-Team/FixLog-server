package com.fixlog.common.exception;

import com.fixlog.common.code.Code;
import com.fixlog.common.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Response handleBusinessException(BusinessException e) {
        LOGGER.warn("BusinessException: code={}, message={}", e.getCode(), e.getMessage());
        return Response.failure(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Response handleException(Exception e) {
        LOGGER.error("Unhandled exception", e);
        return Response.failure(Code.UNKNOWN, "서버 내부 오류가 발생했습니다.");
    }
}
