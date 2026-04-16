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
        return Webhook.receive("rec_id_123", "UNKNOWN");
    }

    @Test
    @DisplayName("receive 호출 시 PENDING 상태의 Webhook 이 저장된다")
    void receive_saves_pending_webhook() {
        Webhook webhook = createWebhook();
        given(webhookRepository.saveAndFlush(any(Webhook.class))).willReturn(webhook);

        Webhook result = webhookService.receive("rec_id_123", "UNKNOWN");

        assertThat(result.getStatus()).isEqualTo(WebhookStatus.PENDING);
        verify(webhookRepository).saveAndFlush(any(Webhook.class));
    }

    @Test
    @DisplayName("receive 호출 중 webhook-id 중복 저장이 발생하면 기존 웹훅을 재조회해 멱등 처리한다")
    void receive_duplicateKey_returns_existing_webhook() {
        Webhook duplicated = createWebhook();
        duplicated.fail();
        given(webhookRepository.saveAndFlush(any(Webhook.class)))
                .willThrow(new DataIntegrityViolationException("duplicate webhook-id"));
        given(webhookRepository.findByRecWebhookId("rec_id_123"))
                .willReturn(Optional.of(duplicated));
        given(webhookRepository.save(duplicated)).willReturn(duplicated);

        Webhook result = webhookService.receive("rec_id_123", "UNKNOWN");

        assertThat(result.getStatus()).isEqualTo(WebhookStatus.PENDING);
        assertThat(result.getCompletedAt()).isNull();
        verify(webhookRepository).saveAndFlush(any(Webhook.class));
        verify(webhookRepository).findByRecWebhookId("rec_id_123");
        verify(webhookRepository).save(duplicated);
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
    @DisplayName("complete 호출 시 COMPLETED 상태로 저장된다")
    void complete_saves_completed_status() {
        Webhook webhook = createWebhook();

        webhookService.complete(webhook);

        assertThat(webhook.getStatus()).isEqualTo(WebhookStatus.COMPLETED);
        assertThat(webhook.getCompletedAt()).isNotNull();
        verify(webhookRepository).save(webhook);
    }

    @Test
    @DisplayName("fail 호출 시 FAILED 상태로 저장된다")
    void fail_saves_failed_status() {
        Webhook webhook = createWebhook();

        webhookService.fail(webhook);

        assertThat(webhook.getStatus()).isEqualTo(WebhookStatus.FAILED);
        assertThat(webhook.getCompletedAt()).isNotNull();
        verify(webhookRepository).save(webhook);
    }

    @Test
    @DisplayName("receive 호출 시 recWebhookId 가 올바르게 저장된다")
    void receive_saves_recWebhookId() {
        Webhook webhook = createWebhook();
        given(webhookRepository.saveAndFlush(any(Webhook.class))).willReturn(webhook);

        Webhook result = webhookService.receive("rec_id_123", "UNKNOWN");

        assertThat(result.getRecWebhookId()).isEqualTo("rec_id_123");
    }

    @Test
    @DisplayName("receive 호출 시 receivedAt 이 null 이 아니다")
    void receive_receivedAt_not_null() {
        Webhook webhook = createWebhook();
        given(webhookRepository.saveAndFlush(any(Webhook.class))).willReturn(webhook);

        Webhook result = webhookService.receive("rec_id_123", "UNKNOWN");

        assertThat(result.getReceivedAt()).isNotNull();
    }
}
