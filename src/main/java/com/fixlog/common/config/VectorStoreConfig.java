package com.fixlog.common.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class VectorStoreConfig {

    @Bean(name = "taskExecutor")
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("embedding-");
        executor.initialize();
        return executor;
    }

    /**
     * 이미지 비전 분석 전용 실행기.
     *
     * <p>임베딩과 풀을 나누는 이유는, 한 문서에 이미지를 수십 장 넣고 저장하면 분석 작업이 큐를
     * 가득 채워 문서 색인까지 밀리기 때문이다. 비전 호출은 임베딩보다 훨씬 오래 걸린다.
     *
     * <p>거부 정책을 {@code CallerRunsPolicy}로 둔다. 기본값({@code AbortPolicy})이면 큐가 넘칠 때
     * 예외가 업로드 요청까지 전파돼 업로드가 실패하거나 분석이 조용히 사라진다.
     * 느려지는 편이 유실되는 편보다 낫다.
     */
    @Bean(name = "imageAnalysisExecutor")
    public TaskExecutor imageAnalysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("image-analysis-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSource dataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean("pgVectorJdbcTemplate")
    public JdbcTemplate pgVectorJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * 벡터 테이블 생성 여부. pgvector 전용 DDL이라 Postgres가 아닌 환경(H2 테스트)에서는 꺼야 한다.
     */
    @Value("${fixlog.vectorstore.initialize-schema:true}")
    private boolean initializeVectorSchema;

    @Bean
    public VectorStore vectorStore(
            @Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel,
            @Value("${fixlog.vectorstore.initialize-schema:true}") boolean initializeSchema) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName("document_embeddings")
                .dimensions(1536)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .initializeSchema(initializeVectorSchema)
                .build();
    }
}
