package four_tential.potential.infra.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;

/**
 * Spring AI ChatClient 공통 설정.
 *
 * 스터디 패턴 기반:
 *   {@code @Profile}로 OpenAI / Ollama 전환</li>
 *   시스템 프롬프트는 {@code resources/prompts/*.st} 파일로 관리 (스터디 week2 패턴)</li>
 *   {@code spring.ai.chat.client.enabled=false} 로 자동 구성 비활성화 후 수동 생성</li>
 *
 * 실행 방법:
 *   IntelliJ Active profiles: local,ollama
 *   터미널: ./gradlew bootRun --args='--spring.profiles.active=local,ollama'
 */
@Configuration
public class AiConfig {

    // ================================================================
    //  PromptTemplate Bean — .st 파일 로드
    //  변수 치환: {courseId}, {reviews}
    // ================================================================

    @Bean
    public PromptTemplate reviewSummaryPromptTemplate() {
        return new PromptTemplate(new ClassPathResource("prompts/review-summary.st"));
    }

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

    //  공통 빌더 메서드
    private ChatClient buildChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(Ordered.LOWEST_PRECEDENCE - 1)
                )
                .build();
    }
}