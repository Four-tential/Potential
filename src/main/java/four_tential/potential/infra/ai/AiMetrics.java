package four_tential.potential.infra.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 챗봇 요청과 실제 AI 모델 호출에 대한 메트릭을 한 곳에서 기록하는 전용 컴포넌트
 */
@Component
public class AiMetrics {

    private final MeterRegistry registry;

    private static final String TAG_RESULT = "result";
    private static final String TAG_FEATURE = "feature";
    private static final String TAG_MODEL = "model";
    private static final String TAG_ERROR_TYPE = "errorType";
    private static final String UNKNOWN = "unknown";

    public AiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // 챗봇 요청 건수를 결과(success, rate_limited, unauthenticated, fail)별로 집계
    public void recordChatbotRequest(String result) {
        Counter.builder("chatbot.request")
                .description("Chatbot request count")
                .tag(TAG_RESULT, normalize(result))
                .register(registry)
                .increment();
    }

    // 챗봇 요청 전체 처리 시간을 결과별로 기록
    public void recordChatbotRequestDuration(String result, long nanos) {
        Timer.builder("chatbot.request.duration")
                .description("Chatbot request duration")
                .tag(TAG_RESULT, normalize(result))
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(10))
                .maximumExpectedValue(Duration.ofSeconds(60))
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    // 실제 AI 모델 호출 건수를 모델명과 결과(success, fail) 기준으로 집계
    public void recordChatCallRequest(String feature, String model, String result) {
        Counter.builder("ai.chat.call.request")
                .description("AI chat model call count")
                .tag(TAG_FEATURE, normalize(feature))
                .tag(TAG_MODEL, normalize(model))
                .tag(TAG_RESULT, normalize(result))
                .register(registry)
                .increment();
    }

    // 실제 AI 모델 호출 처리 시간을 모델명과 결과별로 기록
    public void recordChatCallDuration(String feature, String model, String result, long nanos) {
        Timer.builder("ai.chat.call.duration")
                .description("AI chat model call duration")
                .tag(TAG_FEATURE, normalize(feature))
                .tag(TAG_MODEL, normalize(model))
                .tag(TAG_RESULT, normalize(result))
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(50))
                .maximumExpectedValue(Duration.ofSeconds(60))
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    // 실제 AI 모델 호출 실패 수를 모델명과 에러 유형별로 집계
    public void recordChatCallError(String feature, String model, String errorType) {
        Counter.builder("ai.chat.call.error")
                .description("AI chat model call errors")
                .tag(TAG_FEATURE, normalize(feature))
                .tag(TAG_MODEL, normalize(model))
                .tag(TAG_ERROR_TYPE, normalize(errorType))
                .register(registry)
                .increment();
    }

    // 프롬프트 입력 토큰 사용량을 모델별 누적값으로 기록
    public void recordPromptTokens(String feature, String model, Integer tokens) {
        incrementTokenCounter("ai.chat.tokens.prompt", "AI prompt token usage", feature, model, tokens);
    }

    // 응답 출력 토큰 사용량을 모델별 누적값으로 기록
    public void recordCompletionTokens(String feature, String model, Integer tokens) {
        incrementTokenCounter("ai.chat.tokens.completion", "AI completion token usage", feature, model, tokens);
    }

    // 전체 토큰 사용량(prompt + completion)을 모델별 누적값으로 기록
    public void recordTotalTokens(String feature, String model, Integer tokens) {
        incrementTokenCounter("ai.chat.tokens.total", "AI total token usage", feature, model, tokens);
    }

    // 토큰 값이 있을 때만 해당 토큰 메트릭 카운터를 증가
    private void incrementTokenCounter(String metricName, String description, String feature, String model, Integer tokens) {
        if (tokens == null || tokens <= 0) {
            return;
        }

        Counter.builder(metricName)
                .description(description)
                .tag(TAG_FEATURE, normalize(feature))
                .tag(TAG_MODEL, normalize(model))
                .register(registry)
                .increment(tokens.doubleValue());
    }

    // 메트릭 태그 값이 null/blank이면 unknown으로 바꾸고 공백은 밑줄로 정리
    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        return value.trim().replaceAll("\\s+", "_");
    }
}
