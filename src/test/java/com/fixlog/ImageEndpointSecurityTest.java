package com.fixlog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이미지 엔드포인트는 인증을 요구해야 한다.
 *
 * <p>이미지는 그것이 붙은 문서의 조회 권한을 따르고, 서버가 바이트를 직접 내려주므로
 * 인증이 유일한 앞단 관문이다. 열어두면 id만 알면 비로그인으로 모든 워크스페이스의 이미지가 보인다.
 *
 * <p>{@code <img src>}로 불러오려다 401을 만나면 보안 설정을 여는 것이 가장 손쉬운 해결로 보인다.
 * 실제 해법은 클라이언트가 blob으로 받아 object URL로 바꾸는 것이므로, 그 유혹을 막으려 테스트로 고정한다.
 */
@SpringBootTest
class ImageEndpointSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 이 저장소의 여러 테스트가 SecurityContextHolder에 인증을 심는다. 남은 값이 보이면
        // 요청이 인증된 것처럼 통과해 보안 설정이 열려 있어도 이 테스트가 지나갈 수 있다.
        SecurityContextHolder.clearContext();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** {@code anonymous()}로 요청마다 비로그인 상태를 못박아 실행 순서에 흔들리지 않게 한다. */
    @Test
    void 이미지_조회는_인증_없이_할_수_없다() throws Exception {
        mockMvc.perform(get("/api/images/" + UUID.randomUUID()).with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 이미지_업로드도_인증_없이_할_수_없다() throws Exception {
        mockMvc.perform(post("/api/images/upload").with(anonymous()))
                .andExpect(status().isUnauthorized());
    }
}
