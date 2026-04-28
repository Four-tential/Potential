package four_tential.potential.infra.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spring AI ChatClient / VectorStore 공통 설정
 *
 * 로컬 실행 방법:
 *   IntelliJ Active profiles: local,ollama
 *   터미널: ./gradlew bootRun --args='--spring.profiles.active=local,ollama'
 */
@Configuration
public class AiConfig {

    //  PromptTemplate Bean — .st 파일 로드
    //  변수 치환: {courseId}, {reviews}
    @Bean
    public PromptTemplate reviewSummaryPromptTemplate() {
        return new PromptTemplate(new ClassPathResource("ai/prompts/review-summary.st"));
    }

    //  ChatClient Beans

    //  기본 프로필: OpenAI (dev / prod)
    @Bean
    @Profile("!ollama")
    public ChatClient reviewChatClient(
            @Qualifier("openAiChatModel") ChatModel chatModel
    ) {
        return buildChatClient(chatModel);
    }

    //  Ollama 프로필: 로컬 LLM
    @Bean("reviewChatClient")
    @Profile("ollama")
    public ChatClient reviewChatClientOllama(
            @Qualifier("ollamaChatModel") ChatModel chatModel
    ) {
        return buildChatClient(chatModel);
    }

    private ChatClient buildChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(Ordered.LOWEST_PRECEDENCE - 1)
                )
                .build();
    }

    // ─────────────────────────────────────────
    //  VectorStore Bean — 프로젝트 공통 단일 저장소
    //  도메인 구분은 Document 메타데이터로 처리
    //  예: new Document(content, Map.of("domain", "review", "courseId", 1L))
    //
    //  EmbeddingModel 프로파일 분기:
    //    - !ollama → OpenAiEmbeddingModel  (text-embedding-3-small, 1536차원)
    //    - ollama  → OllamaEmbeddingModel  (nomic-embed-text, 768차원)
    // ─────────────────────────────────────────

    @Bean
    @Profile("!ollama")
    public VectorStore vectorStore(
            @Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel
    ) {
        return buildVectorStore(jdbcTemplate, embeddingModel);
    }

    @Bean("vectorStore")
    @Profile("ollama")
    public VectorStore vectorStoreOllama(
            @Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel
    ) {
        return buildVectorStore(jdbcTemplate, embeddingModel);
    }

    private VectorStore buildVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName("potential_vector_store")  // 프로젝트 공통 테이블
                .initializeSchema(true)                     // 테이블 + 인덱스 자동 생성
                .build();
    }
}