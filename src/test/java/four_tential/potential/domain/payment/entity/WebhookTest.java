package four_tential.potential.domain.payment.entity;

import four_tential.potential.domain.payment.enums.WebhookStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class WebhookTest {

    @Test
    @DisplayName("웹훅 수신 시 PENDING 상태로 생성된다")
    void receive_status_pending() {
        Webhook webhook = Webhook.receive("rec_id_123", "Transaction.Paid");
        assertThat(webhook.getStatus()).isEqualTo(WebhookStatus.PENDING);
    }

    @Test
    @DisplayName("웹훅 수신 시 recWebhookId 가 올바르게 저장된다")
    void receive_recWebhookId() {
        Webhook webhook = Webhook.receive("rec_id_123", "Transaction.Paid");
        assertThat(webhook.getRecWebhookId()).isEqualTo("rec_id_123");
    }

    @Test
    @DisplayName("웹훅 수신 시 eventStatus 가 올바르게 저장된다")
    void receive_eventStatus() {
        Webhook webhook = Webhook.receive("rec_id_123", "Transaction.Paid");
        assertThat(webhook.getEventStatus()).isEqualTo("Transaction.Paid");
    }

    @Test
    @DisplayName("웹훅 수신 시 receivedAt 이 null 이 아니다")
    void receive_receivedAt_not_null() {
        Webhook webhook = Webhook.receive("rec_id_123", "Transaction.Paid");
        assertThat(webhook.getReceivedAt()).isNotNull();
    }

    @Test
    @DisplayName("웹훅 수신 시 completedAt 이 null 이다")
    void receive_completedAt_null() {
        Webhook webhook = Webhook.receive("rec_id_123", "Transaction.Paid");
        assertThat(webhook.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("complete 호출 시 COMPLETED 상태로 변경된다")
    void complete_status_completed() {
        Webhook webhook = Webhook.receive("rec_id_123", "Transaction.Paid");
        webhook.complete();
        assertThat(webhook.getStatus()).isEqualTo(WebhookStatus.COMPLETED);
    }

    @Test
    @DisplayName("complete 호출 시 completedAt 이 저장된다")
    void complete_completedAt_not_null() {
        Webhook webhook = Webhook.receive("rec_id_123", "Transaction.Paid");
        webhook.complete();
        assertThat(webhook.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("fail 호출 시 FAILED 상태로 변경된다")
    void fail_status_failed() {
        Webhook webhook = Webhook.receive("rec_id_123", "Transaction.Paid");
        webhook.fail();
        assertThat(webhook.getStatus()).isEqualTo(WebhookStatus.FAILED);
    }

    @Test
    @DisplayName("fail 호출 시 completedAt 이 저장된다")
    void fail_completedAt_not_null() {
        Webhook webhook = Webhook.receive("rec_id_123", "Transaction.Paid");
        webhook.fail();
        assertThat(webhook.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("updateEventStatus 호출 시 eventStatus 가 변경된다")
    void updateEventStatus_changes_event_status() {
        Webhook webhook = Webhook.receive("rec_id_123", "UNKNOWN");
        webhook.updateEventStatus("WebhookTransactionPaid");
        assertThat(webhook.getEventStatus()).isEqualTo("WebhookTransactionPaid");
    }
}