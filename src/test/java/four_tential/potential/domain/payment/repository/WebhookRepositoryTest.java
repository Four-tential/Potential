package four_tential.potential.domain.payment.repository;

import four_tential.potential.domain.payment.entity.Webhook;
import four_tential.potential.infra.redis.RedisTestContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class WebhookRepositoryTest extends RedisTestContainer {

    @Autowired
    private WebhookRepository webhookRepository;

    @Test
    @DisplayName("existsByRecWebhookId 호출 시 같은 webhook-id 가 있으면 true 를 반환한다")
    void existsByRecWebhookId_returns_true_when_exists() {
        webhookRepository.saveAndFlush(Webhook.createPendingRecord("webhook-1", "UNKNOWN", null));

        boolean result = webhookRepository.existsByRecWebhookId("webhook-1");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("existsByRecWebhookId 호출 시 같은 webhook-id 가 없으면 false 를 반환한다")
    void existsByRecWebhookId_returns_false_when_not_exists() {
        boolean result = webhookRepository.existsByRecWebhookId("missing-webhook");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("existsCompletedByRecWebhookId 호출 시 완료된 웹훅이면 true 를 반환한다")
    void existsCompletedByRecWebhookId_returns_true_when_completed() {
        Webhook webhook = Webhook.createPendingRecord("webhook-completed", "WebhookTransactionPaid", null);
        webhook.markCompleted();
        webhookRepository.saveAndFlush(webhook);

        boolean result = webhookRepository.existsCompletedByRecWebhookId("webhook-completed");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("existsCompletedByRecWebhookId 호출 시 완료되지 않은 웹훅이면 false 를 반환한다")
    void existsCompletedByRecWebhookId_returns_false_when_not_completed() {
        webhookRepository.saveAndFlush(Webhook.createPendingRecord("webhook-pending", "WebhookTransactionPaid", null));

        boolean result = webhookRepository.existsCompletedByRecWebhookId("webhook-pending");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("existsFinishedByRecWebhookId 호출 시 완료된 웹훅이면 true 를 반환한다")
    void existsFinishedByRecWebhookId_returns_true_when_completed() {
        Webhook webhook = Webhook.createPendingRecord("webhook-finished-completed", "WebhookTransactionPaid", null);
        webhook.markCompleted();
        webhookRepository.saveAndFlush(webhook);

        boolean result = webhookRepository.existsFinishedByRecWebhookId("webhook-finished-completed");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("existsFinishedByRecWebhookId 호출 시 실패한 웹훅이면 true 를 반환한다")
    void existsFinishedByRecWebhookId_returns_true_when_failed() {
        Webhook webhook = Webhook.createPendingRecord("webhook-finished-failed", "WebhookTransactionPaid", null);
        webhook.markFailed("PAYMENT_AMOUNT_MISMATCH", "amount mismatch");
        webhookRepository.saveAndFlush(webhook);

        boolean result = webhookRepository.existsFinishedByRecWebhookId("webhook-finished-failed");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("existsFinishedByRecWebhookId 호출 시 대기 중인 웹훅이면 false 를 반환한다")
    void existsFinishedByRecWebhookId_returns_false_when_pending() {
        webhookRepository.saveAndFlush(Webhook.createPendingRecord("webhook-finished-pending", "WebhookTransactionPaid", null));

        boolean result = webhookRepository.existsFinishedByRecWebhookId("webhook-finished-pending");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("findByRecWebhookId 호출 시 webhook-id 가 일치하는 웹훅을 조회한다")
    void findByRecWebhookId_returns_webhook() {
        webhookRepository.saveAndFlush(Webhook.createPendingRecord("webhook-find", "UNKNOWN", null));

        Optional<Webhook> result = webhookRepository.findByRecWebhookId("webhook-find");

        assertThat(result).isPresent();
        assertThat(result.get().getRecWebhookId()).isEqualTo("webhook-find");
    }

    @Test
    @DisplayName("findLatestProcessableByPgKeyAndEventStatus 호출 시 완료되지 않은 최신 Paid 웹훅을 조회한다")
    void findLatestProcessableByPgKeyAndEventStatus_returns_latest_pending_webhook() {
        Webhook oldWebhook = Webhook.createPendingRecord("webhook-old", "WebhookTransactionPaid", null);
        oldWebhook.updatePgKey("pg-key-1");
        ReflectionTestUtils.setField(oldWebhook, "receivedAt", LocalDateTime.now().minusMinutes(1));
        webhookRepository.saveAndFlush(oldWebhook);

        Webhook latestWebhook = Webhook.createPendingRecord("webhook-latest", "WebhookTransactionPaid", null);
        latestWebhook.updatePgKey("pg-key-1");
        ReflectionTestUtils.setField(latestWebhook, "receivedAt", LocalDateTime.now());
        webhookRepository.saveAndFlush(latestWebhook);

        Webhook completedWebhook = Webhook.createPendingRecord("webhook-completed-ignore", "WebhookTransactionPaid", null);
        completedWebhook.updatePgKey("pg-key-1");
        completedWebhook.markCompleted();
        webhookRepository.saveAndFlush(completedWebhook);

        Webhook failedWebhook = Webhook.createPendingRecord("webhook-failed-ignore", "WebhookTransactionPaid", null);
        failedWebhook.updatePgKey("pg-key-1");
        failedWebhook.markFailed("PAYMENT_AMOUNT_MISMATCH", "amount mismatch");
        webhookRepository.saveAndFlush(failedWebhook);

        Optional<Webhook> result = webhookRepository.findLatestProcessableByPgKeyAndEventStatus(
                "pg-key-1",
                "WebhookTransactionPaid"
        );

        assertThat(result).isPresent();
        assertThat(result.get().getRecWebhookId()).isEqualTo("webhook-latest");
    }

    @Test
    @DisplayName("findLatestProcessableByPgKeyAndEventStatus 호출 시 조건에 맞는 웹훅이 없으면 빈 Optional 을 반환한다")
    void findLatestProcessableByPgKeyAndEventStatus_returns_empty_when_not_found() {
        Optional<Webhook> result = webhookRepository.findLatestProcessableByPgKeyAndEventStatus(
                "missing-pg-key",
                "WebhookTransactionPaid"
        );

        assertThat(result).isEmpty();
    }
}
