package four_tential.potential.infra.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import four_tential.potential.infra.ai.AiMetrics;
import four_tential.potential.infra.ai.advisor.AiLoggingAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
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
 *   - 모델 변경 시 potential_vector_store 테이블 재생성 필요 (차원 불일치)
 *   - ChatModel은 ollama 프로파일로 로컬/OpenAI 분기 유지
 */
@Configuration
public class AiConfig {

    @Bean
    public PromptTemplate reviewSummaryPromptTemplate() {
        return new PromptTemplate(new ClassPathResource("ai/prompts/review-summary.st"));
    }

    @Bean
    public AiLoggingAdvisor aiLoggingAdvisor(AiMetrics aiMetrics) {
        return new AiLoggingAdvisor(Ordered.LOWEST_PRECEDENCE - 1, aiMetrics);
    }


    @Bean
    @Profile("!ollama")
    public ChatClient reviewChatClient(
            @Qualifier("openAiChatModel") ChatModel chatModel,
            AiLoggingAdvisor aiLoggingAdvisor
    ) {
        return buildChatClient(chatModel, aiLoggingAdvisor);
    }

    @Bean("reviewChatClient")
    @Profile("ollama")
    public ChatClient reviewChatClientOllama(
            @Qualifier("ollamaChatModel") ChatModel chatModel,
            AiLoggingAdvisor aiLoggingAdvisor
    ) {
        return buildChatClient(chatModel, aiLoggingAdvisor);
    }

    private ChatClient buildChatClient(ChatModel chatModel, AiLoggingAdvisor aiLoggingAdvisor) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(aiLoggingAdvisor)
                .build();
    }

    @Bean
    @Profile("!ollama & !test")
    public ChatClient chatbotChatClient(
            @Qualifier("openAiChatModel") ChatModel chatModel,
            VectorStore vectorStore,
            AiLoggingAdvisor aiLoggingAdvisor
    ) {
        return buildChatbotChatClient(chatModel, vectorStore, aiLoggingAdvisor);
    }

    @Bean("chatbotChatClient")
    @Profile("ollama & !test")
    public ChatClient chatbotChatClientOllama(
            @Qualifier("ollamaChatModel") ChatModel chatModel,
            VectorStore vectorStore,
            AiLoggingAdvisor aiLoggingAdvisor
    ) {
        return buildChatbotChatClient(chatModel, vectorStore, aiLoggingAdvisor);
    }

    private ChatClient buildChatbotChatClient(ChatModel chatModel, VectorStore vectorStore, AiLoggingAdvisor aiLoggingAdvisor) {
        SearchRequest searchRequest = SearchRequest.builder()
                .similarityThreshold(0.3)
                .topK(5)
                .filterExpression("domain == 'policy'")
                .build();

        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        당신은 온라인 수강 플랫폼 'Potential'의 고객지원 FAQ 챗봇입니다.

                        [규칙]
                        1. 사용자 질문과 함께 제공되는 검색 결과(정책 문서)에 있는 내용만 근거로 답변하세요.
                        2. 검색 결과에 해당 내용이 없으면 "해당 내용은 현재 확인이 어렵습니다"라고 안내하세요.
                        3. 반드시 한국어로만 답변하세요. 영어를 섞지 마세요.
                        4. 답변은 2~3문장 이내로 간결하게 작성하세요.
                        5. 정책 문서의 내용을 직접 인용하거나 요약하여 답변하세요.
                        """)
                .defaultAdvisors(
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(searchRequest)
                                .build(),
                        aiLoggingAdvisor
                )
                .build();
    }

    @Bean
    @Profile("!test")
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
