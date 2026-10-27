package com.fixlog.application.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 연관 문서의 벡터 이웃 조회 SQL이 실제 pgvector에서 도는지 확인한다.
 *
 * <p>단위 테스트는 SQL 바깥의 판단(권한·병합·임계값)만 고정한다. 정작 위험한 쪽은 SQL이다 —
 * LATERAL 조인, {@code <=>} 연산자, 이름 있는 파라미터의 IN 목록 전개가 어긋나면 쿼리가 터지거나
 * 조용히 빈 결과를 낸다. H2는 벡터 타입이 없어 이 경로를 전혀 밟지 못한다.
 *
 * <p>임베딩은 손으로 만든 벡터를 쓴다. 검증 대상이 임베딩 품질이 아니라 쿼리이기 때문이다.
 *
 * <p>로컬 Postgres(`docker/docker-compose.yml`)와 `.env.properties`가 있을 때만 실행되며,
 * 없으면 건너뛴다.
 */
class RelatedDocumentSearchIT {

    private static final int DIMENSIONS = 1536;
    private static final String TABLE = "related_it_embeddings";
    private static final String WORKSPACE = "11111111-1111-1111-1111-111111111111";

    private JdbcTemplate jdbcTemplate;
    private RelatedDocumentService service;

    @BeforeEach
    void setUp() throws IOException {
        Path envFile = Path.of(".env.properties");
        assumeTrue(Files.exists(envFile), ".env.properties가 없어 건너뛴다");

        Properties env = new Properties();
        try (var in = Files.newInputStream(envFile)) {
            env.load(in);
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:postgresql://" + env.getProperty("DB_URL") + "/fixlog",
                env.getProperty("DB_USERNAME"), env.getProperty("DB_PASSWORD"));
        dataSource.setDriverClassName("org.postgresql.Driver");

        // 접속을 확인한 뒤에야 필드에 넣는다. 먼저 넣으면 건너뛸 때도 tearDown 이 DROP 을 시도해
        // 테스트가 '건너뜀'이 아니라 '실패'로 집계된다.
        JdbcTemplate candidate = new JdbcTemplate(dataSource);
        try {
            candidate.execute("select 1");
        } catch (Exception e) {
            assumeTrue(false, "로컬 Postgres에 접속할 수 없어 건너뛴다: " + e.getMessage());
        }
        jdbcTemplate = candidate;

        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + TABLE);
        jdbcTemplate.execute("""
                CREATE TABLE %s (
                    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
                    content text,
                    metadata json,
                    embedding vector(%d)
                )""".formatted(TABLE, DIMENSIONS));

        // 권한·병합은 단위 테스트가 맡는다. 여기서는 SQL만 보므로 나머지 협력자는 쓰이지 않는다.
        service = new RelatedDocumentService(
                jdbcTemplate, null, null, null, 0.0, 5, 20, 200, TABLE);
    }

    @AfterEach
    void tearDown() {
        if (jdbcTemplate != null) {
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + TABLE);
        }
    }

    @Test
    void 가까운_벡터를_가진_문서가_더_높은_점수로_나온다() {
        insertChunk("doc-a", "원본", 0, vector(0, 1.0));
        insertChunk("doc-near", "가까운 문서", 0, vector(0, 0.99));
        insertChunk("doc-far", "먼 문서", 0, vector(1, 1.0));

        List<RelatedDocumentService.NeighborChunk> neighbors =
                service.queryNeighbors("doc-a", WORKSPACE, List.of("doc-near", "doc-far"));

        assertThat(neighbors).isNotEmpty();
        double near = maxScoreOf(neighbors, "doc-near");
        double far = maxScoreOf(neighbors, "doc-far");
        assertThat(near).isGreaterThan(far);
    }

    @Test
    void 자기_자신은_결과에_들어가지_않는다() {
        insertChunk("doc-a", "원본", 0, vector(0, 1.0));
        insertChunk("doc-b", "다른 문서", 0, vector(0, 0.9));

        List<RelatedDocumentService.NeighborChunk> neighbors =
                service.queryNeighbors("doc-a", WORKSPACE, List.of("doc-a", "doc-b"));

        assertThat(neighbors).extracting(RelatedDocumentService.NeighborChunk::documentId)
                .doesNotContain("doc-a");
    }

    @Test
    void 접근_가능_목록에_없는_문서는_걸러진다() {
        insertChunk("doc-a", "원본", 0, vector(0, 1.0));
        insertChunk("doc-secret", "비공개", 0, vector(0, 0.999));

        List<RelatedDocumentService.NeighborChunk> neighbors =
                service.queryNeighbors("doc-a", WORKSPACE, List.of("doc-other"));

        assertThat(neighbors).isEmpty();
    }

    @Test
    void 다른_워크스페이스_문서는_걸러진다() {
        insertChunk("doc-a", "원본", 0, vector(0, 1.0));
        insertChunkIn("22222222-2222-2222-2222-222222222222", "doc-b", "남의 워크스페이스", 0, vector(0, 0.999));

        List<RelatedDocumentService.NeighborChunk> neighbors =
                service.queryNeighbors("doc-a", WORKSPACE, List.of("doc-b"));

        assertThat(neighbors).isEmpty();
    }

    @Test
    void 색인되지_않은_문서는_빈_결과다() {
        insertChunk("doc-b", "다른 문서", 0, vector(0, 1.0));

        List<RelatedDocumentService.NeighborChunk> neighbors =
                service.queryNeighbors("doc-없음", WORKSPACE, List.of("doc-b"));

        assertThat(neighbors).isEmpty();
    }

    @Test
    void 소스_청크가_여러_개면_각각의_이웃을_모아_돌려준다() {
        insertChunk("doc-a", "원본", 0, vector(0, 1.0));
        insertChunk("doc-a", "원본", 1, vector(500, 1.0));
        insertChunk("doc-b", "첫 청크와 가까움", 0, vector(0, 0.99));
        insertChunk("doc-c", "둘째 청크와 가까움", 0, vector(500, 0.99));

        List<RelatedDocumentService.NeighborChunk> neighbors =
                service.queryNeighbors("doc-a", WORKSPACE, List.of("doc-b", "doc-c"));

        // 소스 청크 하나만 썼다면 둘 중 하나는 낮은 점수로만 잡혔을 것이다
        assertThat(maxScoreOf(neighbors, "doc-b")).isGreaterThan(0.9);
        assertThat(maxScoreOf(neighbors, "doc-c")).isGreaterThan(0.9);
    }

    private double maxScoreOf(List<RelatedDocumentService.NeighborChunk> neighbors, String documentId) {
        return neighbors.stream()
                .filter(n -> documentId.equals(n.documentId()))
                .mapToDouble(RelatedDocumentService.NeighborChunk::score)
                .max().orElse(-1);
    }

    private void insertChunk(String documentId, String title, int chunkIndex, String embedding) {
        insertChunkIn(WORKSPACE, documentId, title, chunkIndex, embedding);
    }

    private void insertChunkIn(String workspaceId, String documentId, String title,
                               int chunkIndex, String embedding) {
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " (content, metadata, embedding) VALUES (?, ?::json, ?::vector)",
                title + " 본문 " + chunkIndex,
                """
                        {"documentId":"%s","workspaceId":"%s","title":"%s","folderId":"","chunkIndex":%d}"""
                        .formatted(documentId, workspaceId, title, chunkIndex),
                embedding);
    }

    /** {@code hotIndex} 자리만 값을 갖는 희소 벡터. 자리가 같을수록 코사인 유사도가 높아진다. */
    private String vector(int hotIndex, double value) {
        return IntStream.range(0, DIMENSIONS)
                .mapToObj(i -> i == hotIndex ? String.valueOf(value) : "0")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
