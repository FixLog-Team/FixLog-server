package com.fixlog.common.exception;

import com.fixlog.common.code.Code;
import com.fixlog.common.response.Response;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Response> handleBusinessException(BusinessException e) {
        LOGGER.warn("BusinessException: code={}, message={}", e.getCode(), e.getMessage());
        HttpStatus status = resolveHttpStatus(e.getCode());
        return ResponseEntity.status(status).body(Response.failure(e.getCode(), e.getMessage()));
    }

    /** @Valid @RequestBody 검증 실패. 입력 오류이므로 400으로 내린다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response> handleRequestBodyValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        LOGGER.warn("요청 본문 검증 실패: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Response.failure(Code.INVALID_REQUEST,
                        message.isBlank() ? "요청 값이 올바르지 않습니다." : message));
    }

    /** @Validated 컨트롤러의 파라미터 검증 실패(@Min, @Max 등). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Response> handleParameterValidation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        LOGGER.warn("요청 파라미터 검증 실패: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Response.failure(Code.INVALID_REQUEST,
                        message.isBlank() ? "요청 값이 올바르지 않습니다." : message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response> handleException(Exception e) {
        LOGGER.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Response.failure(Code.UNKNOWN, "서버 내부 오류가 발생했습니다."));
    }

    private HttpStatus resolveHttpStatus(Code code) {
        return switch (code) {
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
