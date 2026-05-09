package four_tential.potential.infra.ai;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AiMetricsTest {

    @Test
    @DisplayName("AI 메트릭을 기록하고 태그를 정규화한다")
    void records_metrics_and_normalizes_tags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiMetrics metrics = new AiMetrics(registry);

        metrics.recordChatbotRequest("success");
        metrics.recordChatbotRequestDuration("success", TimeUnit.MILLISECONDS.toNanos(120));

        metrics.recordChatCallRequest("review summary", "gpt-4.1-nano", "success");
        metrics.recordChatCallDuration("review summary", "gpt-4.1-nano", "success", TimeUnit.SECONDS.toNanos(2));
        metrics.recordChatCallError("review summary", "gpt-4.1-nano", "IllegalStateException");

        metrics.recordPromptTokens("review summary", "gpt-4.1-nano", 10);
        metrics.recordCompletionTokens("review summary", "gpt-4.1-nano", 5);
        metrics.recordTotalTokens("review summary", "gpt-4.1-nano", 15);

        metrics.recordChatCallRequest("   ", null, "");
        metrics.recordPromptTokens("review summary", "gpt-4.1-nano", 0);
        metrics.recordCompletionTokens("review summary", "gpt-4.1-nano", null);

        assertThat(registry.get("chatbot.request")
                .tag("result", "success")
                .counter().count()).isEqualTo(1.0);

        assertThat(registry.get("chatbot.request.duration")
                .tag("result", "success")
                .timer().count()).isEqualTo(1);

        assertThat(registry.get("ai.chat.call.request")
                .tag("feature", "review_summary")
                .tag("model", "gpt-4.1-nano")
                .tag("result", "success")
                .counter().count()).isEqualTo(1.0);

        assertThat(registry.get("ai.chat.call.request")
                .tag("feature", "unknown")
                .tag("model", "unknown")
                .tag("result", "unknown")
                .counter().count()).isEqualTo(1.0);

        assertThat(registry.get("ai.chat.call.duration")
                .tag("feature", "review_summary")
                .tag("model", "gpt-4.1-nano")
                .tag("result", "success")
                .timer().count()).isEqualTo(1);

        assertThat(registry.get("ai.chat.call.error")
                .tag("feature", "review_summary")
                .tag("model", "gpt-4.1-nano")
                .tag("errorType", "IllegalStateException")
                .counter().count()).isEqualTo(1.0);

        assertThat(registry.get("ai.chat.tokens.prompt")
                .tag("feature", "review_summary")
                .tag("model", "gpt-4.1-nano")
                .counter().count()).isEqualTo(10.0);

        assertThat(registry.get("ai.chat.tokens.completion")
                .tag("feature", "review_summary")
                .tag("model", "gpt-4.1-nano")
                .counter().count()).isEqualTo(5.0);

        assertThat(registry.get("ai.chat.tokens.total")
                .tag("feature", "review_summary")
                .tag("model", "gpt-4.1-nano")
                .counter().count()).isEqualTo(15.0);
    }
}
