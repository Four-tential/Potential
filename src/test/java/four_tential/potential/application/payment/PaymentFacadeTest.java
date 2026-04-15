package four_tential.potential.application.payment;

import four_tential.potential.domain.payment.entity.Webhook;
import four_tential.potential.domain.payment.port.PaymentGateway;
import four_tential.potential.infra.portone.PortOneWebhookHandler;
import io.portone.sdk.server.errors.WebhookVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentFacadeTest {

    @InjectMocks
    private PaymentFacade paymentFacade;

    @Mock
    private PaymentService paymentService;

    @Mock
    private WebhookService webhookService;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PortOneWebhookHandler portOneWebhookHandler;

    @Mock
    private TransactionTemplate transactionTemplate;

    private Webhook mockWebhook;

    @BeforeEach
    void setUp() {
        mockWebhook = Webhook.receive("test-webhook-id", "UNKNOWN");
    }

    @Test
    @DisplayName("중복 웹훅은 무시된다")
    void handleWebhook_ignores_duplicate() throws WebhookVerificationException {
        given(webhookService.isDuplicate("test-webhook-id")).willReturn(true);

        paymentFacade.handleWebhook(
                "{}", "test-webhook-id", "timestamp", "signature");

        verify(webhookService, never()).receive(anyString(), anyString());
    }

    @Test
    @DisplayName("중복이 아닌 웹훅은 수신 기록이 저장된다")
    void handleWebhook_saves_new_webhook() throws WebhookVerificationException {
        given(webhookService.isDuplicate(anyString())).willReturn(false);
        given(transactionTemplate.execute(any()))
                .willReturn(mockWebhook);
        given(portOneWebhookHandler.verify(
                anyString(), anyString(), anyString(), anyString()))
                .willThrow(new WebhookVerificationException("검증 실패", new RuntimeException()));

        assertThatThrownBy(() ->
                paymentFacade.handleWebhook(
                        "{}", "test-webhook-id", "timestamp", "signature"))
                .isInstanceOf(WebhookVerificationException.class);

        verify(transactionTemplate).execute(any());
    }

    @Test
    @DisplayName("서명 검증 실패 시 WebhookVerificationException 이 발생한다")
    void handleWebhook_throws_when_verification_fails()
            throws WebhookVerificationException {
        given(webhookService.isDuplicate(anyString())).willReturn(false);
        given(transactionTemplate.execute(any())).willReturn(mockWebhook);
        given(portOneWebhookHandler.verify(
                anyString(), anyString(), anyString(), anyString()))
                .willThrow(new WebhookVerificationException("서명 검증 실패", new RuntimeException()));

        assertThatThrownBy(() ->
                paymentFacade.handleWebhook(
                        "{}", "test-webhook-id", "timestamp", "signature"))
                .isInstanceOf(WebhookVerificationException.class);
    }
}