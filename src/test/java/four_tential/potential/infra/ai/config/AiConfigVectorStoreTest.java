package four_tential.potential.infra.ai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * AiConfig VectorStore Bean 생성 테스트
 *
 * 임베딩 모델 단일화 (OpenAI text-embedding-3-small) 이후
 * vectorStore Bean 하나만 존재
 */
class AiConfigVectorStoreTest {

    private final AiConfig aiConfig = new AiConfig();

    @Test
    @DisplayName("vectorStore Bean 정상 생성")
    void vectorStore_created_successfully() {
        // given
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        // when
        VectorStore vectorStore = aiConfig.vectorStore(jdbcTemplate, embeddingModel);

        // then
        assertThat(vectorStore).isNotNull();
    }

    @Test
    @DisplayName("동일한 파라미터로 생성된 VectorStore는 서로 다른 인스턴스")
    void vectorStore_returns_new_instance_each_time() {
        // given
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        // when
        VectorStore first = aiConfig.vectorStore(jdbcTemplate, embeddingModel);
        VectorStore second = aiConfig.vectorStore(jdbcTemplate, embeddingModel);

        // then
        assertThat(first).isNotSameAs(second);
    }
}