package four_tential.potential.infra.ai.advisor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import four_tential.potential.infra.ai.AiMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
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
}