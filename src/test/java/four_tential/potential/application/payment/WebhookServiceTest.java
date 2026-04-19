package four_tential.potential.application.payment;

import four_tential.potential.domain.payment.entity.Webhook;
import four_tential.potential.domain.payment.enums.WebhookStatus;
import four_tential.potential.domain.payment.repository.WebhookRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @InjectMocks
    private WebhookService webhookService;

    @Mock
    private WebhookRepository webhookRepository;

    @Mock
    private EntityManager entityManager;

    private Webhook createWebhook() {
        return Webhook.createPendingRecord("rec_id_123", "UNKNOWN", null);
    }

    @Test
    @DisplayName("saveIncomingWebhook 호출 시 PENDING 상태의 Webhook 이 저장된다")
    void saveIncomingWebhook_saves_pending_webhook() {
        Webhook webhook = createWebhook();
        given(webhookRepository.saveAndFlush(any(Webhook.class))).willReturn(webhook);

        Webhook result = webhookService.saveIncomingWebhook("rec_id_123", "UNKNOWN", null);

        assertThat(result.getStatus()).isEqualTo(WebhookStatus.PENDING);
        verify(webhookRepository).saveAndFlush(any(Webhook.class));
    }

    @Test
    @DisplayName("saveIncomingWebhook 호출 중 webhook-id 중복 저장이 발생하면 기존 웹훅을 재조회해 멱등 처리한다")
    void saveIncomingWebhook_duplicateKey_returns_existing_webhook() {
        Webhook duplicated = createWebhook();
        duplicated.markFailed(null, null);
        given(webhookRepository.saveAndFlush(any(Webhook.class)))
                .willThrow(new DataIntegrityViolationException("duplicate webhook-id"));
        given(webhookRepository.findByRecWebhookId("rec_id_123"))
                .willReturn(Optional.of(duplicated));

        Webhook result = webhookService.saveIncomingWebhook("rec_id_123", "UNKNOWN", null);

        assertThat(result.getStatus()).isEqualTo(WebhookStatus.FAILED);
        verify(webhookRepository).saveAndFlush(any(Webhook.class));
        verify(entityManager).clear();
        verify(webhookRepository).findByRecWebhookId("rec_id_123");
    }

    @Test
    @DisplayName("saveIncomingWebhook 호출 중 완료된 중복 웹훅이면 상태를 바꾸지 않고 기존 웹훅을 반환한다")
    void saveIncomingWebhook_duplicateKey_completed_returns_existing_webhook() {
        Webhook duplicated = createWebhook();
        duplicated.markCompleted();
        given(webhookRepository.saveAndFlush(any(Webhook.class)))
                .willThrow(new DataIntegrityViolationException("duplicate webhook-id"));
        given(webhookRepository.findByRecWebhookId("rec_id_123"))
                .willReturn(Optional.of(duplicated));

        Webhook result = webhookService.saveIncomingWebhook("rec_id_123", "UNKNOWN", null);

        assertThat(result.getStatus()).isEqualTo(WebhookStatus.COMPLETED);
        verify(webhookRepository).saveAndFlush(any(Webhook.class));
        verify(entityManager).clear();
        verify(webhookRepository).findByRecWebhookId("rec_id_123");
    }

    @Test
    @DisplayName("saveIncomingWebhook duplicate pending updates payload and saves existing webhook")
    void saveIncomingWebhook_duplicateKey_pending_updates_payload() {
        Webhook duplicated = createWebhook();
        given(webhookRepository.saveAndFlush(any(Webhook.class)))
                .willThrow(new DataIntegrityViolationException("duplicate webhook-id"));
        given(webhookRepository.findByRecWebhookId("rec_id_123"))
                .willReturn(Optional.of(duplicated));
        given(webhookRepository.save(duplicated)).willReturn(duplicated);

        Webhook result = webhookService.saveIncomingWebhook(
                "rec_id_123",
                "WebhookTransactionPaid",
                "{\"type\":\"WebhookTransactionPaid\"}"
        );

        assertThat(result.getStatus()).isEqualTo(WebhookStatus.PENDING);
        assertThat(result.getEventStatus()).isEqualTo("WebhookTransactionPaid");
        assertThat(result.getPayload()).isEqualTo("{\"type\":\"WebhookTransactionPaid\"}");
        verify(webhookRepository).save(duplicated);
    }

    @Test
    @DisplayName("isFinished 호출 시 종료된 웹훅이면 true 를 반환한다")
    void isFinished_returns_true_when_finished() {
        given(webhookRepository.existsFinishedByRecWebhookId("rec_id_123")).willReturn(true);

        boolean result = webhookService.isFinished("rec_id_123");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("findPendingPaidWebhook 호출 시 처리 가능한 Paid 웹훅을 반환한다")
    void findPendingPaidWebhook_returns_webhook() {
        Webhook webhook = createWebhook();
        given(webhookRepository.findLatestProcessableByPgKeyAndEventStatus("pg-key-1", "WebhookTransactionPaid"))
                .willReturn(Optional.of(webhook));

        Optional<Webhook> result = webhookService.findPendingPaidWebhook("pg-key-1");

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
    @DisplayName("prepareRetry 호출 시 실패 웹훅을 다시 PENDING 상태로 저장한다")
    void prepareRetry_saves_pending_status() {
        Webhook webhook = createWebhook();
        given(webhookRepository.save(webhook)).willReturn(webhook);

        Webhook result = webhookService.prepareRetry(webhook, "WebhookTransactionPaid");

        assertThat(result.getStatus()).isEqualTo(WebhookStatus.PENDING);
        assertThat(result.getEventStatus()).isEqualTo("WebhookTransactionPaid");
        assertThat(result.getCompletedAt()).isNull();
        verify(webhookRepository).save(webhook);
    }

    @Test
    @DisplayName("deferPaidWebhook 호출 시 pgKey 와 이벤트 타입을 저장하고 PENDING 상태로 보류한다")
    void deferPaidWebhook_saves_pgKey_and_pending_status() {
        Webhook webhook = createWebhook();
        given(webhookRepository.save(webhook)).willReturn(webhook);

        Webhook result = webhookService.deferPaidWebhook(webhook, "WebhookTransactionPaid", "pg-key-1");

        assertThat(result.getStatus()).isEqualTo(WebhookStatus.PENDING);
        assertThat(result.getEventStatus()).isEqualTo("WebhookTransactionPaid");
        assertThat(result.getPgKey()).isEqualTo("pg-key-1");
        verify(webhookRepository).save(webhook);
    }

    @Test
    @DisplayName("completeWebhook 호출 시 COMPLETED 상태로 저장된다")
    void completeWebhook_saves_completed_status() {
        Webhook webhook = createWebhook();

        webhookService.completeWebhook(webhook);

        assertThat(webhook.getStatus()).isEqualTo(WebhookStatus.COMPLETED);
        assertThat(webhook.getCompletedAt()).isNotNull();
        verify(webhookRepository).save(webhook);
    }

    @Test
    @DisplayName("failWebhook 호출 시 FAILED 상태로 저장된다")
    void failWebhook_saves_failed_status() {
        Webhook webhook = createWebhook();

        webhookService.failWebhook(webhook, null, null);

        assertThat(webhook.getStatus()).isEqualTo(WebhookStatus.FAILED);
        assertThat(webhook.getCompletedAt()).isNotNull();
        verify(webhookRepository).save(webhook);
    }

    @Test
    @DisplayName("failWebhook 호출 시 실패 이유와 메시지를 저장한다")
    void failWebhook_saves_reason_and_message() {
        Webhook webhook = createWebhook();

        webhookService.failWebhook(webhook, "PAYMENT_AMOUNT_MISMATCH", "amount mismatch");

        assertThat(webhook.getStatus()).isEqualTo(WebhookStatus.FAILED);
        assertThat(webhook.getFailReason()).isEqualTo("PAYMENT_AMOUNT_MISMATCH");
        assertThat(webhook.getFailMessage()).isEqualTo("amount mismatch");
        verify(webhookRepository).save(webhook);
    }

    @Test
    @DisplayName("failWebhook with null webhook only logs and does not save")
    void fail_nullWebhook_doesNotSave() {
        webhookService.failWebhook(null, "WEBHOOK_RECEIVE_FAILED", "receive failed");

        verify(webhookRepository, never()).save(any(Webhook.class));
    }

    @Test
    @DisplayName("failDeferredPaidWebhook 호출 시 보류 중인 Paid 웹훅을 FAILED 상태로 저장한다")
    void failDeferredPaidWebhook_marks_webhook_failed() {
        Webhook webhook = createWebhook();
        given(webhookRepository.findLatestProcessableByPgKeyAndEventStatus("pg-key-1", "WebhookTransactionPaid"))
                .willReturn(Optional.of(webhook));

        webhookService.failDeferredPaidWebhook("pg-key-1", "PAYMENT_CREATE_REJECTED", "rejected");

        assertThat(webhook.getStatus()).isEqualTo(WebhookStatus.FAILED);
        assertThat(webhook.getFailReason()).isEqualTo("PAYMENT_CREATE_REJECTED");
        assertThat(webhook.getFailMessage()).isEqualTo("rejected");
        verify(webhookRepository).save(webhook);
    }

    @Test
    @DisplayName("saveIncomingWebhook 호출 시 recWebhookId 가 올바르게 저장된다")
    void saveIncomingWebhook_saves_recWebhookId() {
        Webhook webhook = createWebhook();
        given(webhookRepository.saveAndFlush(any(Webhook.class))).willReturn(webhook);

        Webhook result = webhookService.saveIncomingWebhook("rec_id_123", "UNKNOWN", null);

        assertThat(result.getRecWebhookId()).isEqualTo("rec_id_123");
    }

    @Test
    @DisplayName("saveIncomingWebhook 호출 시 payload 를 저장한다")
    void saveIncomingWebhook_saves_payload() {
        Webhook webhook = Webhook.createPendingRecord("rec_id_123", "UNKNOWN", "{\"type\":\"test\"}");
        given(webhookRepository.saveAndFlush(any(Webhook.class))).willReturn(webhook);

        Webhook result = webhookService.saveIncomingWebhook("rec_id_123", "UNKNOWN", "{\"type\":\"test\"}");

        assertThat(result.getPayload()).isEqualTo("{\"type\":\"test\"}");
    }

    @Test
    @DisplayName("saveIncomingWebhook 호출 시 receivedAt 이 null 이 아니다")
    void saveIncomingWebhook_receivedAt_not_null() {
        Webhook webhook = createWebhook();
        given(webhookRepository.saveAndFlush(any(Webhook.class))).willReturn(webhook);

        Webhook result = webhookService.saveIncomingWebhook("rec_id_123", "UNKNOWN", null);

        assertThat(result.getReceivedAt()).isNotNull();
    }

    @Test
    @DisplayName("failDeferredPaidWebhook 호출 시 처리 가능한 Paid 웹훅이 없으면 아무 작업도 하지 않는다")
    void failDeferredPaidWebhook_noPendingWebhook_doesNothing() {
        given(webhookRepository.findLatestProcessableByPgKeyAndEventStatus("pg-key-empty", "WebhookTransactionPaid"))
                .willReturn(Optional.empty());

        webhookService.failDeferredPaidWebhook("pg-key-empty", "PAYMENT_CREATE_REJECTED", "rejected");

        verify(webhookRepository, never()).save(any(Webhook.class));
    }

    @Test
    @DisplayName("merge 호출 시 현재 트랜잭션 안에서 웹훅 변경사항을 저장한다")
    void merge_saves_webhook() {
        Webhook webhook = createWebhook();
        webhook.updateEventStatus("WebhookTransactionPaid");
        given(webhookRepository.save(webhook)).willReturn(webhook);

        Webhook result = webhookService.merge(webhook);

        assertThat(result.getEventStatus()).isEqualTo("WebhookTransactionPaid");
        verify(webhookRepository).save(webhook);
    }

    @Test
    @DisplayName("중복 webhook-id 재조회에도 기존 웹훅이 없으면 원래 저장 예외를 다시 던진다")
    void saveIncomingWebhook_duplicateKeyButMissing_throwsOriginalException() {
        DataIntegrityViolationException duplicateException =
                new DataIntegrityViolationException("duplicate webhook-id");

        given(webhookRepository.saveAndFlush(any(Webhook.class)))
                .willThrow(duplicateException);
        given(webhookRepository.findByRecWebhookId("rec_id_missing"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                webhookService.saveIncomingWebhook("rec_id_missing", "UNKNOWN", "{}"))
                .isSameAs(duplicateException);

        verify(entityManager).clear();
        verify(webhookRepository).findByRecWebhookId("rec_id_missing");
    }

}
