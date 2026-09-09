package com.fixlog;

import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.exception.GlobalExceptionHandler;
import com.fixlog.common.response.Response;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;
import java.util.Set;

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

    // 입력 오류가 500으로 나가면 클라이언트가 "서버 잘못이니 재시도"로 오해한다.
    // 게다가 정상적인 잘못된 요청이 ERROR 로그로 쌓여 실제 장애 신호를 묻는다.
    @Test
    void 요청_본문_검증_실패는_400과_위반_필드를_함께_돌려준다() throws Exception {
        MethodParameter parameter = new MethodParameter(
                DummyController.class.getDeclaredMethod("create", Object.class), 0);
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "title", "제목은 필수입니다."));

        ResponseEntity<Response> response =
                handler.handleRequestBodyValidation(new MethodArgumentNotValidException(parameter, binding));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Code.INVALID_REQUEST, response.getBody().getCode());
        assertEquals("title: 제목은 필수입니다.", response.getBody().getMessage());
    }

    @Test
    void 파라미터_검증_실패도_400으로_응답한다() {
        ResponseEntity<Response> response =
                handler.handleParameterValidation(new ConstraintViolationException(Set.of()));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Code.INVALID_REQUEST, response.getBody().getCode());
        // 위반 목록이 비어도 메시지가 비지 않아야 클라이언트가 원인을 읽을 수 있다.
        assertEquals("요청 값이 올바르지 않습니다.", response.getBody().getMessage());
    }

    /** MethodParameter를 만들기 위한 더미. 실제 요청 처리에는 쓰이지 않는다. */
    private static final class DummyController {
        @SuppressWarnings("unused")
        void create(Object request) {
        }
    }
}
