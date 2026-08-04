package com.fixlog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 검색 사전 필터가 실제 pgvector에서 도는지 확인한다 (FR-SHR-006).
 *
 * <p>다른 테스트는 H2라 벡터 경로를 타지 않는다. 여기서 확인하려는 것은 우리가 만든 필터
 * 표현식이 <b>jsonb 메타데이터 조회 SQL로 제대로 번역되는가</b>다. 번역이 어긋나면 필터가
 * 조용히 무시되거나 쿼리가 터진다.
 *
 * <p>임베딩은 고정 벡터를 돌려주는 대역으로 대신한다. 검증 대상이 임베딩 품질이 아니라
 * 필터이고, 외부 API 호출과 비용을 끌어들일 이유가 없기 때문이다.
 *
 * <p>로컬 Postgres(`docker/docker-compose.yml`)가 떠 있고 `.env.properties`가 있을 때만
 * 실행되며, 없으면 건너뛴다.
 */
class VectorSearchFilterIT {

    private static final int DIMENSIONS = 1536;
    private static final String TABLE = "filter_it_embeddings";

    private JdbcTemplate jdbcTemplate;
    private PgVectorStore vectorStore;

    /** 항상 같은 벡터를 돌려준다. 유사도 순위가 아니라 필터가 검증 대상이다. */
    private static final EmbeddingModel FIXED_EMBEDDING = new EmbeddingModel() {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            for (int i = 0; i < request.getInstructions().size(); i++) {
                embeddings.add(new Embedding(fixedVector(), i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return fixedVector();
        }

        @Override
        public int dimensions() {
            return DIMENSIONS;
        }

        private static float[] fixedVector() {
            float[] vector = new float[DIMENSIONS];
            vector[0] = 1.0f;
            return vector;
        }
    };

    @BeforeEach
    void setUp() throws IOException {
        Path envFile = Path.of(".env.properties");
        assumeTrue(Files.exists(envFile), "로컬 .env.properties가 없어 건너뛴다");

        Properties env = new Properties();
        try (var in = Files.newInputStream(envFile)) {
            env.load(in);
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:postgresql://" + env.getProperty("DB_URL") + "/fixlog",
                env.getProperty("DB_USERNAME"), env.getProperty("DB_PASSWORD"));
        dataSource.setDriverClassName("org.postgresql.Driver");

        jdbcTemplate = new JdbcTemplate(dataSource);
        try {
            jdbcTemplate.execute("select 1");
        } catch (Exception e) {
            assumeTrue(false, "로컬 Postgres에 접속할 수 없어 건너뛴다: " + e.getMessage());
        }

        jdbcTemplate.execute("DROP TABLE IF EXISTS " + TABLE);
        vectorStore = PgVectorStore.builder(jdbcTemplate, FIXED_EMBEDDING)
                .vectorTableName(TABLE)
                .dimensions(DIMENSIONS)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .initializeSchema(true)
                .build();
        vectorStore.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        if (jdbcTemplate != null) {
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + TABLE);
        }
    }

    private Document chunk(String documentId, UUID workspaceId, String text) {
        return new Document(text, Map.of(
                "documentId", documentId,
                "workspaceId", workspaceId.toString(),
                "title", "제목",
                "folderId", ""));
    }

    @Test
    void 워크스페이스와_접근_가능_문서로_사전_필터가_걸린다() {
        UUID mine = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        vectorStore.add(List.of(
                chunk("doc-allowed", mine, "JVM 힙 덤프를 떠서 확인했다"),
                chunk("doc-denied", mine, "권한이 없는 문서의 내용"),
                chunk("doc-other-workspace", other, "다른 워크스페이스의 문서")));

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query("힙 덤프")
                .topK(10)
                .filterExpression(b.and(
                        b.eq("workspaceId", mine.toString()),
                        b.in("documentId", new Object[]{"doc-allowed"})).build())
                .build());

        List<String> ids = results.stream()
                .map(d -> (String) d.getMetadata().get("documentId"))
                .toList();

        assertEquals(List.of("doc-allowed"), ids,
                "필터가 무시되면 세 건이 모두 나온다");
    }

    @Test
    void 워크스페이스가_다르면_문서_ID가_맞아도_걸러진다() {
        UUID mine = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        vectorStore.add(List.of(chunk("doc-1", other, "다른 워크스페이스의 문서")));

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query("문서")
                .topK(10)
                .filterExpression(b.and(
                        b.eq("workspaceId", mine.toString()),
                        b.in("documentId", new Object[]{"doc-1"})).build())
                .build());

        assertTrue(results.isEmpty(), "워크스페이스 조건이 함께 걸려야 한다");
    }
}
