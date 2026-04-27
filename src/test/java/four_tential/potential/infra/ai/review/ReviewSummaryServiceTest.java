package four_tential.potential.infra.ai.review;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReviewSummaryServiceTest {

    @Test
    @DisplayName("후기가 없으면 빈 문자열 반환")
    void summarize_empty_reviews() {
        // given
        ChatClient chatClient = mock(ChatClient.class);
        PromptTemplate promptTemplate = mock(PromptTemplate.class);

        ReviewSummaryService service =
                new ReviewSummaryService(chatClient, promptTemplate);

        // when
        String result = service.summarize(1L, List.of());

        // then
        assertThat(result).isEqualTo("");
        verify(chatClient, never()).prompt((String) any());
    }

    @Test
    @DisplayName("정상적으로 요약 요청")
    void summarize_success() {
        // given
        ChatClient chatClient = mock(ChatClient.class);
        PromptTemplate promptTemplate = mock(PromptTemplate.class);

        Prompt prompt = mock(Prompt.class);

        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(promptTemplate.create(anyMap())).thenReturn(prompt);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("요약 결과");

        ReviewSummaryService service =
                new ReviewSummaryService(chatClient, promptTemplate);

        // when
        String result = service.summarize(1L, List.of("좋아요", "별로예요"));

        // then
        assertThat(result).isEqualTo("요약 결과");
    }
}