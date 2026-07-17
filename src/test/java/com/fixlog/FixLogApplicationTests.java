package com.fixlog;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class FixLogApplicationTests {

	// pgvector 기반 VectorStore는 H2 테스트 DB에서 스키마 초기화가 불가능하므로 목으로 대체
	@MockitoBean
	private VectorStore vectorStore;

	@Test
	void contextLoads() {
	}

}
