package four_tential.potential.application.payment;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PaymentMetricsTest {

    private SimpleMeterRegistry registry;
    private PaymentMetrics paymentMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        paymentMetrics = new PaymentMetrics(registry);
    }

    @Test
    @DisplayName("결제 관련 counter 메트릭을 태그와 함께 기록한다")
    void recordCounterMetrics() {
        paymentMetrics.recordPrepareRequest(" success ");
        paymentMetrics.recordWebhookReceived(" PAID WEBHOOK ");
        paymentMetrics.recordWebhookFailed(" PAID WEBHOOK ", null);
        paymentMetrics.recordPaymentConfirmSuccess();
        paymentMetrics.recordCancelRequest(" NO_AVAILABLE_SEATS ");
        paymentMetrics.recordRefundRequest("fail");

        assertThat(registry.get("payment.prepare.request")
                .tag("result", "success")
                .counter()
                .count()).isEqualTo(1.0);

        assertThat(registry.get("payment.webhook.received")
                .tag("eventType", "PAID_WEBHOOK")
                .counter()
                .count()).isEqualTo(1.0);

        assertThat(registry.get("payment.webhook.failed")
                .tag("eventType", "PAID_WEBHOOK")
                .tag("reason", "unknown")
                .counter()
                .count()).isEqualTo(1.0);

        assertThat(registry.get("payment.confirm.success")
                .counter()
                .count()).isEqualTo(1.0);

        assertThat(registry.get("payment.cancel.request")
                .tag("reason", "NO_AVAILABLE_SEATS")
                .counter()
                .count()).isEqualTo(1.0);

        assertThat(registry.get("payment.refund.request")
                .tag("result", "fail")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("결제 관련 timer 메트릭을 count와 duration으로 기록한다")
    void recordTimerMetrics() {
        paymentMetrics.recordPrepareDuration("success", TimeUnit.MILLISECONDS.toNanos(50));
        paymentMetrics.recordWebhookDuration("cancelled", "WebhookTransactionPaid", TimeUnit.MILLISECONDS.toNanos(75));
        paymentMetrics.recordRefundDuration("fail", TimeUnit.MILLISECONDS.toNanos(125));

        assertThat(registry.get("payment.prepare.duration")
                .tag("result", "success")
                .timer()
                .count()).isEqualTo(1);

        assertThat(registry.get("payment.prepare.duration")
                .tag("result", "success")
                .timer()
                .totalTime(TimeUnit.MILLISECONDS)).isCloseTo(50.0, within(0.001));

        assertThat(registry.get("payment.webhook.duration")
                .tag("result", "cancelled")
                .tag("eventType", "WebhookTransactionPaid")
                .timer()
                .count()).isEqualTo(1);

        assertThat(registry.get("payment.webhook.duration")
                .tag("result", "cancelled")
                .tag("eventType", "WebhookTransactionPaid")
                .timer()
                .totalTime(TimeUnit.MILLISECONDS)).isCloseTo(75.0, within(0.001));

        assertThat(registry.get("payment.refund.duration")
                .tag("result", "fail")
                .timer()
                .count()).isEqualTo(1);

        assertThat(registry.get("payment.refund.duration")
                .tag("result", "fail")
                .timer()
                .totalTime(TimeUnit.MILLISECONDS)).isCloseTo(125.0, within(0.001));
    }

    @Test
    @DisplayName("blank 또는 null 태그 값은 unknown으로 정규화한다")
    void normalizeBlankAndNullTagsToUnknown() {
        paymentMetrics.recordPrepareRequest("   ");
        paymentMetrics.recordRefundRequest(null);
        paymentMetrics.recordWebhookReceived(null);

        assertThat(registry.get("payment.prepare.request")
                .tag("result", "unknown")
                .counter()
                .count()).isEqualTo(1.0);

        assertThat(registry.get("payment.refund.request")
                .tag("result", "unknown")
                .counter()
                .count()).isEqualTo(1.0);

        assertThat(registry.get("payment.webhook.received")
                .tag("eventType", "unknown")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
