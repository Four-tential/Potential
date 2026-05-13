package four_tential.potential.infra.ai.chatbot;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.infra.ai.AiMetrics;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatbotServiceTest {

    private ChatClient chatbotChatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec responseSpec;
    private ChatbotRateLimiter chatbotRateLimiter;
    private ChatbotService chatbotService;
    private AiMetrics aiMetrics;

    @BeforeEach
    void setUp() {
        chatbotChatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        responseSpec = mock(ChatClient.CallResponseSpec.class);
        chatbotRateLimiter = mock(ChatbotRateLimiter.class);
        aiMetrics = mock(AiMetrics.class);

        when(chatbotChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);

        chatbotService = new ChatbotService(chatbotChatClient, chatbotRateLimiter, aiMetrics);
    }

    private MemberPrincipal principal() {
        return new MemberPrincipal(UUID.randomUUID(), "test@test.com", "ROLE_USER");
    }

    @Test
    @DisplayName("ask — ChatClient 체인을 거쳐 content 결과를 반환")
    void ask_returns_content() {
        when(responseSpec.content()).thenReturn("환불은 코스 시작 7일 전까지만 가능합니다.");
        MemberPrincipal principal = principal();

        String answer = chatbotService.ask(principal, "환불 가능 기간이 언제인가요");

        assertThat(answer).isEqualTo("환불은 코스 시작 7일 전까지만 가능합니다.");
        verify(chatbotRateLimiter).check(principal.memberId(), principal.role());
        verify(chatbotChatClient).prompt();
        verify(requestSpec).user("환불 가능 기간이 언제인가요");
        verify(requestSpec).call();
        verify(responseSpec).content();
    }

    @Test
    @DisplayName("ask — content 가 null 이면 그대로 null 반환")
    void ask_returns_null_when_content_null() {
        when(responseSpec.content()).thenReturn(null);

        String answer = chatbotService.ask(principal(), "아무 질문");

        assertThat(answer).isNull();
    }

    @Test
    @DisplayName("ask — principal 이 null 이면 ServiceErrorException")
    void ask_throws_when_principal_null() {
        assertThatThrownBy(() -> chatbotService.ask(null, "질문"))
                .isInstanceOf(ServiceErrorException.class);

        verify(chatbotRateLimiter, never()).check(any(), anyString());
        verify(chatbotChatClient, never()).prompt();
    }
}
