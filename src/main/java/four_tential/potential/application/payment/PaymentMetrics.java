package four_tential.potential.application.payment;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 결제 시나리오에서 사용하는 메트릭 기록 전용 컴포넌트
 */
@Component
public class PaymentMetrics {

    private final MeterRegistry registry;

    public PaymentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // 결제 준비 요청 수를 결과(success, duplicate, fail)별로 집계
    public void recordPrepareRequest(String result) {
        Counter.builder("payment.prepare.request")
                .description("Payment prepare requests")
                .tag("result", normalize(result))
                .register(registry)
                .increment();
    }

    // 결제 준비 처리 시간을 결과별로 기록
    public void recordPrepareDuration(String result, long nanos) {
        Timer.builder("payment.prepare.duration")
                .description("Payment prepare duration")
                .tag("result", normalize(result))
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(5))
                .maximumExpectedValue(Duration.ofSeconds(5))
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    // 수신한 결제 웹훅 수를 eventType별로 집계
    public void recordWebhookReceived(String eventType) {
        Counter.builder("payment.webhook.received")
                .description("Payment webhook received count")
                .tag("eventType", normalize(eventType))
                .register(registry)
                .increment();
    }

    // 웹훅 처리 실패 수를 eventType과 실패 이유별로 집계
    public void recordWebhookFailed(String eventType, String reason) {
        Counter.builder("payment.webhook.failed")
                .description("Payment webhook failed count")
                .tag("eventType", normalize(eventType))
                .tag("reason", normalize(reason))
                .register(registry)
                .increment();
    }

    // 웹훅 처리 시간을 결과와 eventType 기준으로 기록
    public void recordWebhookDuration(String result, String eventType, long nanos) {
        Timer.builder("payment.webhook.duration")
                .description("Payment webhook processing duration")
                .tag("result", normalize(result))
                .tag("eventType", normalize(eventType))
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(5))
                .maximumExpectedValue(Duration.ofSeconds(10))
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    // 결제 확정 완료 건수를 집계
    public void recordPaymentConfirmSuccess() {
        Counter.builder("payment.confirm.success")
                .description("Payment confirmation success count")
                .register(registry)
                .increment();
    }

    // 결제 취소 요청 수를 취소 사유별로 집계
    public void recordCancelRequest(String reason) {
        Counter.builder("payment.cancel.request")
                .description("Payment cancel request count")
                .tag("reason", normalize(reason))
                .register(registry)
                .increment();
    }

    // 환불 요청 건수를 성공/실패 결과별로 집계
    public void recordRefundRequest(String result) {
        Counter.builder("payment.refund.request")
                .description("Refund request count")
                .tag("result", normalize(result))
                .register(registry)
                .increment();
    }

    // 환불 처리 시간을 성공/실패 결과별로 기록
    public void recordRefundDuration(String result, long nanos) {
        Timer.builder("payment.refund.duration")
                .description("Refund processing duration")
                .tag("result", normalize(result))
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(5))
                .maximumExpectedValue(Duration.ofSeconds(10))
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    // 메트릭 태그 값이 null/blank이거나 공백이 많은 경우 안전한 문자열로 정규화
    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().replaceAll("\\s+", "_");
    }
}

