package four_tential.potential.domain.payment.entity;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.payment.enums.WebhookStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookTest {

    @Test
    @DisplayName("웹훅 수신 시 PENDING 상태로 생성된다")
    void receive_status_pending() {
        Webhook webhook = Webhook.receive("rec_id_123", "Transaction.Paid");

        assertThat(webhook.getStatus()).isEqualTo(WebhookStatus.PENDING);
    }

    @Test
    @DisplayName("웹훅 수신 시 recWebhookId 가 저장된다")
    void receive_recWebhookId() {
        Webhook webhook = Webhook.receive("rec_id_123", "Transaction.Paid");

        assertThat(webhook.getRecWebhookId()).isEqualTo("rec_id_123");
    }

    @Test
    @DisplayName("웹훅 수신 시 eventStatus 가 저장된다")
    void receive_eventStatus() {
        Webhook webhook = Webhook.receive("rec_id_123", "Transaction.Paid");

        assertThat(webhook.getEventStatus()).isEqualTo("Transaction.Paid");
    }

    @Test
    @DisplayName("웹훅 수신 시 receivedAt 이 저장된다")
    void receive_receivedAt_not_null() {
        Webhook webhook = Webhook.receive("rec_id_123", "Transaction.Paid");

        assertThat(webhook.getReceivedAt()).isNotNull();
    }

    @Test
    @DisplayName("웹훅 수신 시 completedAt 은 null 이다")
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
    @DisplayName("실패한 웹훅은 재시도 시 PENDING 상태로 돌아갈 수 있다")
    void failed_can_retry_to_pending() {
        Webhook webhook = Webhook.receive("rec_id_123", "Transaction.Paid");
        webhook.fail();
        webhook.retry("UNKNOWN");

        assertThat(webhook.getStatus()).isEqualTo(WebhookStatus.PENDING);
        assertThat(webhook.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("COMPLETED 상태에서는 FAILED 상태로 변경할 수 없다")
    void completed_cannot_change_to_failed() {
        Webhook webhook = Webhook.receive("rec_id_123", "Transaction.Paid");
        webhook.complete();

        assertThatThrownBy(webhook::fail)
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("COMPLETED 상태에서는 PENDING 상태로 재시도할 수 없다")
    void completed_cannot_retry_to_pending() {
        Webhook webhook = Webhook.receive("rec_id_123", "Transaction.Paid");
        webhook.complete();

        assertThatThrownBy(() -> webhook.retry("UNKNOWN"))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("updateEventStatus 호출 시 eventStatus 가 변경된다")
    void updateEventStatus_changes_event_status() {
        Webhook webhook = Webhook.receive("rec_id_123", "UNKNOWN");
        webhook.updateEventStatus("WebhookTransactionPaid");

        assertThat(webhook.getEventStatus()).isEqualTo("WebhookTransactionPaid");
    }
}
