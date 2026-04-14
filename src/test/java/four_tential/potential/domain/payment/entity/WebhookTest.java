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
    @DisplayName("웹훅 수신 시 reciveAt 이 null 이 아니다")
    void receive_reciveAt_not_null() {
        Webhook webhook = Webhook.receive("rec_id_123", "Transaction.Paid");
        assertThat(webhook.getReceivedAt()).isNotNull();
    }

    @Test
    @DisplayName("웹훅 수신 시 completedAt 이 null 이다")
    void receive_completedAt_null() {
        Webhook webhook = Webhook.receive("rec_id_123", "Transaction.Paid");
        assertThat(webhook.getCompletedAt()).isNull();
    }

}