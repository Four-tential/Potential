package four_tential.potential.infra.ai.advisor;

import four_tential.potential.infra.ai.AiMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.concurrent.TimeUnit;

/**
 * 실제 AI 모델 호출의 시작/종료/실패/토큰 사용량을 로깅하고 메트릭으로 기록하는 커스텀 Advisor
 */
@Slf4j
public class AiLoggingAdvisor implements CallAdvisor, StreamAdvisor {

    public static final String CONTEXT_FEATURE = "feature";
    public static final String FEATURE_CHATBOT = "chatbot";
    public static final String FEATURE_REVIEW_SUMMARY = "review_summary";

    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_FAIL = "fail";
    private static final String UNKNOWN = "unknown";

    private final int order;
    private final AiMetrics aiMetrics;

    public AiLoggingAdvisor(int order, AiMetrics aiMetrics) {
        this.order = order;
        this.aiMetrics = aiMetrics;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    // 동기 AI 호출 전후를 감싸면서 호출 수, 응답 시간, 에러, 토큰 사용량을 기록
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String feature = extractFeature(request);
        String model = extractModel(request);
        long startedAt = System.nanoTime();

        log.info("[AI_CALL] 요청 시작. feature={} model={}", feature, model);

        try {
            ChatClientResponse response = chain.nextCall(request);
            long elapsed = System.nanoTime() - startedAt;

            aiMetrics.recordChatCallRequest(feature, model, RESULT_SUCCESS);
            aiMetrics.recordChatCallDuration(feature, model, RESULT_SUCCESS, elapsed);

            UsageSnapshot usage = extractUsage(response);
            aiMetrics.recordPromptTokens(feature, model, usage.promptTokens());
            aiMetrics.recordCompletionTokens(feature, model, usage.completionTokens());
            aiMetrics.recordTotalTokens(feature, model, usage.totalTokens());

            log.info(
                    "[AI_CALL] 응답 완료. feature={} model={} durationMs={} promptTokens={} completionTokens={} totalTokens={}",
                    feature,
                    model,
                    TimeUnit.NANOSECONDS.toMillis(elapsed),
                    usage.promptTokens(),
                    usage.completionTokens(),
                    usage.totalTokens()
            );
            return response;
        } catch (RuntimeException e) {
            long elapsed = System.nanoTime() - startedAt;

            aiMetrics.recordChatCallRequest(feature, model, RESULT_FAIL);
            aiMetrics.recordChatCallDuration(feature, model, RESULT_FAIL, elapsed);
            aiMetrics.recordChatCallError(feature, model, e.getClass().getSimpleName());

            log.warn(
                    "[AI_CALL] 요청 실패. feature={} model={} durationMs={} errorType={}",
                    feature,
                    model,
                    TimeUnit.NANOSECONDS.toMillis(elapsed),
                    e.getClass().getSimpleName(),
                    e
            );
            throw e;
        }
    }

    // 스트리밍 AI 호출 전후를 감싸면서 호출 수, 응답 시간, 에러를 기록
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        String feature = extractFeature(request);
        String model = extractModel(request);
        long startedAt = System.nanoTime();

        log.info("[AI_STREAM] 요청 시작. feature={} model={}", feature, model);

        return chain.nextStream(request)
                .doOnComplete(() -> {
                    long elapsed = System.nanoTime() - startedAt;
                    aiMetrics.recordChatCallRequest(feature, model, RESULT_SUCCESS);
                    aiMetrics.recordChatCallDuration(feature, model, RESULT_SUCCESS, elapsed);

                    log.info("[AI_STREAM] 응답 완료. feature={} model={} durationMs={}", feature, model, TimeUnit.NANOSECONDS.toMillis(elapsed));
                })
                .doOnError(throwable -> {
                    long elapsed = System.nanoTime() - startedAt;
                    aiMetrics.recordChatCallRequest(feature, model, RESULT_FAIL);
                    aiMetrics.recordChatCallDuration(feature, model, RESULT_FAIL, elapsed);
                    aiMetrics.recordChatCallError(feature, model, throwable.getClass().getSimpleName());

                    log.warn(
                            "[AI_STREAM] 요청 실패. feature={} model={} durationMs={} errorType={}",
                            feature,
                            model,
                            TimeUnit.NANOSECONDS.toMillis(elapsed),
                            throwable.getClass().getSimpleName(),
                            throwable
                    );
                });
    }

    // 요청 객체에서 모델명을 꺼내고, 실패하면 unknown으로 대체
    private String extractModel(ChatClientRequest request) {
        try {
            return request.prompt().getOptions() != null
                    ? request.prompt().getOptions().getModel()
                    : UNKNOWN;
        } catch (Exception e) {
            return UNKNOWN;
        }
    }

    private String extractFeature(ChatClientRequest request) {
        try {
            Object feature = request.context().get(CONTEXT_FEATURE);
            return feature != null ? feature.toString() : UNKNOWN;
        } catch (Exception e) {
            return UNKNOWN;
        }
    }

    // 응답 metadata에서 prompt/completion/total token 정보를 null-safe 하게 추출
    private UsageSnapshot extractUsage(ChatClientResponse response) {
        try {
            ChatResponse chatResponse = response != null ? response.chatResponse() : null;
            ChatResponseMetadata metadata = chatResponse != null ? chatResponse.getMetadata() : null;
            Usage usage = metadata != null ? metadata.getUsage() : null;

            if (usage == null) {
                return UsageSnapshot.empty();
            }

            Integer promptTokens = usage.getPromptTokens();
            Integer completionTokens = usage.getCompletionTokens();
            Integer totalTokens = usage.getTotalTokens();

            return new UsageSnapshot(promptTokens, completionTokens, totalTokens);
        } catch (Exception e) {
            return UsageSnapshot.empty();
        }
    }

    // usage 정보를 한 번에 다루기 위한 내부 보조 값 객체
    private record UsageSnapshot(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        private static UsageSnapshot empty() {
            return new UsageSnapshot(null, null, null);
        }
    }
}