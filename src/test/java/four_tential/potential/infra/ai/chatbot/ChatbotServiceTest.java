package four_tential.potential.infra.ai.chatbot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatbotServiceTest {

    private ChatClient chatbotChatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec responseSpec;
    private ChatbotService chatbotService;

    @BeforeEach
    void setUp() {
        chatbotChatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatbotChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);

        chatbotService = new ChatbotService(chatbotChatClient);
    }

    @Test
    @DisplayName("ask — ChatClient 체인을 거쳐 content 결과를 반환")
    void ask_returns_content() {
        when(responseSpec.content()).thenReturn("환불은 코스 시작 7일 전까지만 가능합니다.");

        String answer = chatbotService.ask("환불 가능 기간이 언제인가요");

        assertThat(answer).isEqualTo("환불은 코스 시작 7일 전까지만 가능합니다.");
        verify(chatbotChatClient).prompt();
        verify(requestSpec).user("환불 가능 기간이 언제인가요");
        verify(requestSpec).call();
        verify(responseSpec).content();
    }

    @Test
    @DisplayName("ask — content 가 null 이면 그대로 null 반환")
    void ask_returns_null_when_content_null() {
        when(responseSpec.content()).thenReturn(null);

        String answer = chatbotService.ask("아무 질문");

        assertThat(answer).isNull();
    }
}
