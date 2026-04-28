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
 *
 * 임베딩 모델: OpenAI text-embedding-3-small (1536차원) 고정
 *   - ChatModel은 ollama 프로파일로 로컬/OpenAI 분기 유지
 */
@Configuration
public class AiConfig {

    // ─────────────────────────────────────────
    //  PromptTemplate Bean — .st 파일 로드
    //  변수 치환: {courseId}, {reviews}
    // ─────────────────────────────────────────

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

    //  VectorStore Bean — OpenAI 임베딩 모델 단일화
    //  text-embedding-3-small (1536차원) 고정
    //  로컬/dev/prod 환경 동일 — 차원 불일치 마이그레이션 문제 방지
    @Bean
    public VectorStore vectorStore(
            @Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel
    ) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName("potential_vector_store")
                .initializeSchema(true)
                .build();
    }
}