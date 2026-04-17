package four_tential.potential.application.payment;

import four_tential.potential.domain.payment.entity.Webhook;
import four_tential.potential.domain.payment.enums.WebhookStatus;
import four_tential.potential.domain.payment.repository.WebhookRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @InjectMocks
    private WebhookService webhookService;

    @Mock
    private WebhookRepository webhookRepository;

    private Webhook createWebhook() {
        return Webhook.createPendingRecord("rec_id_123", "UNKNOWN", null);
    }

    @Test
    @DisplayName("receive 호출 시 PENDING 상태의 Webhook 이 저장된다")
    void receive_saves_pending_webhook() {
        Webhook webhook = createWebhook();
        given(webhookRepository.saveAndFlush(any(Webhook.class))).willReturn(webhook);

        Webhook result = webhookService.recordReceivedWebhook("rec_id_123", "UNKNOWN", null);

        assertThat(result.getStatus()).isEqualTo(WebhookStatus.PENDING);
        verify(webhookRepository).saveAndFlush(any(Webhook.class));
    }

    @Test
    @DisplayName("receive 호출 중 webhook-id 중복 저장이 발생하면 기존 웹훅을 재조회해 멱등 처리한다")
    void receive_duplicateKey_returns_existing_webhook() {
        Webhook duplicated = createWebhook();
        duplicated.markFailed(null, null);
        given(webhookRepository.saveAndFlush(any(Webhook.class)))
                .willThrow(new DataIntegrityViolationException("duplicate webhook-id"));
        given(webhookRepository.findByRecWebhookId("rec_id_123"))
                .willReturn(Optional.of(duplicated));

        Webhook result = webhookService.recordReceivedWebhook("rec_id_123", "UNKNOWN", null);

        assertThat(result.getStatus()).isEqualTo(WebhookStatus.FAILED);
        verify(webhookRepository).saveAndFlush(any(Webhook.class));
        verify(webhookRepository).findByRecWebhookId("rec_id_123");
    }

    @Test
    @DisplayName("receive 호출 중 완료된 중복 웹훅이면 상태를 바꾸지 않고 기존 웹훅을 반환한다")
    void receive_duplicateKey_completed_returns_existing_webhook() {
        Webhook duplicated = createWebhook();
        duplicated.markCompleted();
        given(webhookRepository.saveAndFlush(any(Webhook.class)))
                .willThrow(new DataIntegrityViolationException("duplicate webhook-id"));
        given(webhookRepository.findByRecWebhookId("rec_id_123"))
                .willReturn(Optional.of(duplicated));

        Webhook result = webhookService.recordReceivedWebhook("rec_id_123", "UNKNOWN", null);

        assertThat(result.getStatus()).isEqualTo(WebhookStatus.COMPLETED);
        verify(webhookRepository).saveAndFlush(any(Webhook.class));
        verify(webhookRepository).findByRecWebhookId("rec_id_123");
    }

    @Test
    @DisplayName("isDuplicate 호출 시 중복이면 true 를 반환한다")
    void isDuplicate_returns_true_when_duplicate() {
        given(webhookRepository.existsByRecWebhookId(anyString()))
                .willReturn(true);

        boolean result = webhookService.isDuplicate("rec_id_123");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isDuplicate 호출 시 중복이 아니면 false 를 반환한다")
    void isDuplicate_returns_false_when_not_duplicate() {
        given(webhookRepository.existsByRecWebhookId(anyString()))
                .willReturn(false);

        boolean result = webhookService.isDuplicate("rec_id_123");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isCompleted 호출 시 완료된 웹훅이면 true 를 반환한다")
    void isCompleted_returns_true_when_completed() {
        given(webhookRepository.existsCompletedByRecWebhookId("rec_id_123")).willReturn(true);

        boolean result = webhookService.isCompleted("rec_id_123");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isCompleted 호출 시 완료된 웹훅이 아니면 false 를 반환한다")
    void isCompleted_returns_false_when_not_completed() {
        given(webhookRepository.existsCompletedByRecWebhookId("rec_id_123")).willReturn(false);

        boolean result = webhookService.isCompleted("rec_id_123");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isFinished 호출 시 종료된 웹훅이면 true 를 반환한다")
    void isFinished_returns_true_when_finished() {
        given(webhookRepository.existsFinishedByRecWebhookId("rec_id_123")).willReturn(true);

        boolean result = webhookService.isFinished("rec_id_123");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("findProcessablePaidWebhook 호출 시 처리 가능한 Paid 웹훅을 반환한다")
    void findProcessablePaidWebhook_returns_webhook() {
        Webhook webhook = createWebhook();
        given(webhookRepository.findLatestProcessableByPgKeyAndEventStatus("pg-key-1", "WebhookTransactionPaid"))
                .willReturn(Optional.of(webhook));

        Optional<Webhook> result = webhookService.findProcessablePaidWebhook("pg-key-1");

        assertThat(result).contains(webhook);
    }

    @Test
    @DisplayName("updateEventStatus 호출 시 실제 웹훅 이벤트 타입을 저장한다")
    void updateEventStatus_saves_event_status() {
        Webhook webhook = createWebhook();
        given(webhookRepository.save(webhook)).willReturn(webhook);

        Webhook result = webhookService.updateEventStatus(webhook, "PlainWebhook");

        assertThat(result.getEventStatus()).isEqualTo("PlainWebhook");
        verify(webhookRepository).save(webhook);
    }

    @Test
    @DisplayName("retry 호출 시 실패 웹훅을 다시 PENDING 상태로 저장한다")
    void retry_saves_pending_status() {
        Webhook webhook = createWebhook();
        given(webhookRepository.save(webhook)).willReturn(webhook);

        Webhook result = webhookService.markWebhookPendingForRetry(webhook, "WebhookTransactionPaid");

        assertThat(result.getStatus()).isEqualTo(WebhookStatus.PENDING);
        assertThat(result.getEventStatus()).isEqualTo("WebhookTransactionPaid");
        assertThat(result.getCompletedAt()).isNull();
        verify(webhookRepository).save(webhook);
    }

    @Test
    @DisplayName("defer 호출 시 pgKey 와 이벤트 타입을 저장하고 PENDING 상태로 보류한다")
    void defer_saves_pgKey_and_pending_status() {
        Webhook webhook = createWebhook();
        given(webhookRepository.save(webhook)).willReturn(webhook);

        Webhook result = webhookService.deferPaidWebhookUntilPaymentSaved(webhook, "WebhookTransactionPaid", "pg-key-1");

        assertThat(result.getStatus()).isEqualTo(WebhookStatus.PENDING);
        assertThat(result.getEventStatus()).isEqualTo("WebhookTransactionPaid");
        assertThat(result.getPgKey()).isEqualTo("pg-key-1");
        verify(webhookRepository).save(webhook);
    }

    @Test
    @DisplayName("complete 호출 시 COMPLETED 상태로 저장된다")
    void complete_saves_completed_status() {
        Webhook webhook = createWebhook();

        webhookService.recordCompletedWebhook(webhook);

        assertThat(webhook.getStatus()).isEqualTo(WebhookStatus.COMPLETED);
        assertThat(webhook.getCompletedAt()).isNotNull();
        verify(webhookRepository).save(webhook);
    }

    @Test
    @DisplayName("fail 호출 시 FAILED 상태로 저장된다")
    void fail_saves_failed_status() {
        Webhook webhook = createWebhook();

        webhookService.recordFailedWebhook(webhook, null, null);

        assertThat(webhook.getStatus()).isEqualTo(WebhookStatus.FAILED);
        assertThat(webhook.getCompletedAt()).isNotNull();
        verify(webhookRepository).save(webhook);
    }

    @Test
    @DisplayName("fail 호출 시 실패 이유와 메시지를 저장한다")
    void fail_saves_reason_and_message() {
        Webhook webhook = createWebhook();

        webhookService.recordFailedWebhook(webhook, "PAYMENT_AMOUNT_MISMATCH", "amount mismatch");

        assertThat(webhook.getStatus()).isEqualTo(WebhookStatus.FAILED);
        assertThat(webhook.getFailReason()).isEqualTo("PAYMENT_AMOUNT_MISMATCH");
        assertThat(webhook.getFailMessage()).isEqualTo("amount mismatch");
        verify(webhookRepository).save(webhook);
    }

    @Test
    @DisplayName("receive 호출 시 recWebhookId 가 올바르게 저장된다")
    void receive_saves_recWebhookId() {
        Webhook webhook = createWebhook();
        given(webhookRepository.saveAndFlush(any(Webhook.class))).willReturn(webhook);

        Webhook result = webhookService.recordReceivedWebhook("rec_id_123", "UNKNOWN", null);

        assertThat(result.getRecWebhookId()).isEqualTo("rec_id_123");
    }

    @Test
    @DisplayName("receive 호출 시 payload 를 저장한다")
    void receive_saves_payload() {
        Webhook webhook = Webhook.createPendingRecord("rec_id_123", "UNKNOWN", "{\"type\":\"test\"}");
        given(webhookRepository.saveAndFlush(any(Webhook.class))).willReturn(webhook);

        Webhook result = webhookService.recordReceivedWebhook("rec_id_123", "UNKNOWN", "{\"type\":\"test\"}");

        assertThat(result.getPayload()).isEqualTo("{\"type\":\"test\"}");
    }

    @Test
    @DisplayName("receive 호출 시 receivedAt 이 null 이 아니다")
    void receive_receivedAt_not_null() {
        Webhook webhook = createWebhook();
        given(webhookRepository.saveAndFlush(any(Webhook.class))).willReturn(webhook);

        Webhook result = webhookService.recordReceivedWebhook("rec_id_123", "UNKNOWN", null);

        assertThat(result.getReceivedAt()).isNotNull();
    }
}
