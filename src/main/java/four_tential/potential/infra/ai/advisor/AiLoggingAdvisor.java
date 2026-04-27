package four_tential.potential.infra.ai.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * AI 요청 처리 시간 및 토큰 정보를 로깅하는 커스텀 Advisor
 * 추후 커스텀 Advisor를 기능 별로 확장 시켜
 * 토큰 사용량 추적, 특정 조건에서만 접근 차단, 모니터링 연동 기능과 같이 확장 가능
 */
@Slf4j
public class AiLoggingAdvisor implements CallAdvisor, StreamAdvisor {

    private final int order;

    public AiLoggingAdvisor(int order) {
        this.order = order;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        log.info("[AI 요청 시작] model={}", extractModel(request));
        long start = System.currentTimeMillis();

        ChatClientResponse response = chain.nextCall(request);

        long elapsed = System.currentTimeMillis() - start;
        log.info("[AI 응답 완료] 소요시간={}ms", elapsed);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        log.info("[AI 스트림 시작] model={}", extractModel(request));
        long start = System.currentTimeMillis();

        return chain.nextStream(request)
                .doOnComplete(() -> {
                    long elapsed = System.currentTimeMillis() - start;
                    log.info("[AI 스트림 완료] 소요시간={}ms", elapsed);
                });
    }

    private String extractModel(ChatClientRequest request) {
        try {
            return request.prompt().getOptions() != null
                    ? request.prompt().getOptions().getModel()
                    : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}