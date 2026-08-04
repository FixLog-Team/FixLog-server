package com.fixlog;

import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.exception.GlobalExceptionHandler;
import com.fixlog.common.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 권한_부족은_403으로_응답한다() {
        ResponseEntity<Response> response =
                handler.handleBusinessException(new BusinessException(Code.FORBIDDEN, "권한이 없습니다."));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(Code.FORBIDDEN, response.getBody().getCode());
        assertEquals("권한이 없습니다.", response.getBody().getMessage());
    }

    // 인증 실패(401)와 권한 부족(403)이 같은 상태로 뭉개지면 클라이언트가 재로그인 유도와
    // 권한 요청을 구분할 수 없다. 실패 코드마다 상태가 갈리는지 함께 고정한다.
    @Test
    void 실패_코드는_서로_다른_상태로_구분된다() {
        Map<Code, HttpStatus> expected = Map.of(
                Code.UNAUTHORIZED, HttpStatus.UNAUTHORIZED,
                Code.FORBIDDEN, HttpStatus.FORBIDDEN,
                Code.NOT_FOUND, HttpStatus.NOT_FOUND,
                Code.INVALID_REQUEST, HttpStatus.BAD_REQUEST
        );

        expected.forEach((code, status) ->
                assertEquals(status,
                        handler.handleBusinessException(new BusinessException(code, "")).getStatusCode(),
                        code + "는 " + status + "로 응답해야 한다"));
    }
}
