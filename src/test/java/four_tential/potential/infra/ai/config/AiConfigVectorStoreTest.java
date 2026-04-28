package four_tential.potential.infra.ai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiConfigVectorStoreTest {

    private final AiConfig aiConfig = new AiConfig();

    @Test
    @DisplayName("!ollama 프로파일 — vectorStore Bean 정상 생성")
    void vectorStore_openai_profile() {
        // given
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        // when
        VectorStore vectorStore = aiConfig.vectorStore(jdbcTemplate, embeddingModel);

        // then
        assertThat(vectorStore).isNotNull();
    }

    @Test
    @DisplayName("ollama 프로파일 — vectorStoreOllama Bean 정상 생성")
    void vectorStore_ollama_profile() {
        // given
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        // when
        VectorStore vectorStore = aiConfig.vectorStoreOllama(jdbcTemplate, embeddingModel);

        // then
        assertThat(vectorStore).isNotNull();
    }

    @Test
    @DisplayName("두 프로파일의 VectorStore는 서로 다른 인스턴스")
    void vectorStore_instances_are_independent() {
        // given
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        // when
        VectorStore openaiStore = aiConfig.vectorStore(jdbcTemplate, embeddingModel);
        VectorStore ollamaStore = aiConfig.vectorStoreOllama(jdbcTemplate, embeddingModel);

        // then
        assertThat(openaiStore).isNotSameAs(ollamaStore);
    }
}