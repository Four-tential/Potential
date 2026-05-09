package four_tential.potential.infra.ai.advisor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import four_tential.potential.infra.ai.AiMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AiLoggingAdvisorTest {

    @Test
    @DisplayName("adviseCall 정상 흐름")
    void adviseCall_success() {
        // given
        AiLoggingAdvisor advisor = new AiLoggingAdvisor(1, new AiMetrics(new SimpleMeterRegistry()));

        ChatClientRequest request = mock(ChatClientRequest.class);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        ChatClientResponse response = mock(ChatClientResponse.class);

        when(chain.nextCall(request)).thenReturn(response);

        // when
        ChatClientResponse result = advisor.adviseCall(request, chain);

        // then
        assertThat(result).isEqualTo(response);
        verify(chain).nextCall(request);
    }

    @Test
    @DisplayName("adviseStream 정상 흐름")
    void adviseStream_success() {
        // given
        AiLoggingAdvisor advisor = new AiLoggingAdvisor(1, new AiMetrics(new SimpleMeterRegistry()));

        ChatClientRequest request = mock(ChatClientRequest.class);
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);

        ChatClientResponse response = mock(ChatClientResponse.class);

        when(chain.nextStream(request)).thenReturn(Flux.just(response));

        // when
        Flux<ChatClientResponse> result = advisor.adviseStream(request, chain);

        // then
        assertThat(result.collectList().block()).containsExactly(response);
        verify(chain).nextStream(request);
    }

    @Test
    @DisplayName("extractModel 예외 케이스 커버")
    void extractModel_exception() {
        // given
        AiLoggingAdvisor advisor = new AiLoggingAdvisor(1, new AiMetrics(new SimpleMeterRegistry()));

        ChatClientRequest request = mock(ChatClientRequest.class);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);

        when(request.prompt()).thenThrow(new RuntimeException());
        when(chain.nextCall(any())).thenReturn(mock(ChatClientResponse.class));

        // when
        ChatClientResponse result = advisor.adviseCall(request, chain);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("adviseCall 실패 시 unknown 태그와 fail 메트릭을 기록한다")
    void adviseCall_records_fail_metrics_with_unknown_tags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiLoggingAdvisor advisor = new AiLoggingAdvisor(1, new AiMetrics(registry));

        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt("질문"))
                .context(Map.of())
                .build();

        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(request)).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> advisor.adviseCall(request, chain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(registry.get("ai.chat.call.request")
                .tag("feature", "unknown")
                .tag("model", "unknown")
                .tag("result", "fail")
                .counter().count()).isEqualTo(1.0);

        assertThat(registry.get("ai.chat.call.duration")
                .tag("feature", "unknown")
                .tag("model", "unknown")
                .tag("result", "fail")
                .timer().count()).isEqualTo(1);

        assertThat(registry.get("ai.chat.call.error")
                .tag("feature", "unknown")
                .tag("model", "unknown")
                .tag("errorType", "IllegalStateException")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("adviseStream 실패 시 fail 메트릭과 error 메트릭을 기록한다")
    void adviseStream_records_fail_metrics_on_error() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiLoggingAdvisor advisor = new AiLoggingAdvisor(1, new AiMetrics(registry));

        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt("질문"))
                .context(Map.of(
                        AiLoggingAdvisor.CONTEXT_FEATURE,
                        AiLoggingAdvisor.FEATURE_CHATBOT
                ))
                .build();

        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(request)).thenReturn(Flux.error(new IllegalArgumentException("stream fail")));

        assertThatThrownBy(() -> advisor.adviseStream(request, chain).collectList().block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("stream fail");

        assertThat(registry.get("ai.chat.call.request")
                .tag("feature", "chatbot")
                .tag("model", "unknown")
                .tag("result", "fail")
                .counter().count()).isEqualTo(1.0);

        assertThat(registry.get("ai.chat.call.duration")
                .tag("feature", "chatbot")
                .tag("model", "unknown")
                .tag("result", "fail")
                .timer().count()).isEqualTo(1);

        assertThat(registry.get("ai.chat.call.error")
                .tag("feature", "chatbot")
                .tag("model", "unknown")
                .tag("errorType", "IllegalArgumentException")
                .counter().count()).isEqualTo(1.0);
    }
}