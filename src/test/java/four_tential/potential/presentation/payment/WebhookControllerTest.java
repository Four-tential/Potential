package four_tential.potential.presentation.payment;

import four_tential.potential.application.payment.PaymentFacade;
import four_tential.potential.infra.portone.PortOneWebhookVerifier;
import io.portone.sdk.server.errors.WebhookVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookControllerTest {

    private MockMvc mockMvc;
    private PaymentFacade paymentFacade;
    private PortOneWebhookVerifier portOneWebhookVerifier;

    @BeforeEach
    void setUp() {
        paymentFacade = Mockito.mock(PaymentFacade.class);
        portOneWebhookVerifier = Mockito.mock(PortOneWebhookVerifier.class);
        WebhookController controller = new WebhookController(paymentFacade, portOneWebhookVerifier);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("웹훅 서명 검증 성공 시 검증된 webhook을 Facade로 전달한다")
    void receiveWebhook_success() throws Exception {
        String rawBody = "{\"type\":\"PAID\"}";
        io.portone.sdk.server.webhook.Webhook verified =
                Mockito.mock(io.portone.sdk.server.webhook.Webhook.class);

        Mockito.when(portOneWebhookVerifier.verify(rawBody, "webhook-1", "signature-1", "timestamp-1"))
                .thenReturn(verified);
        doNothing().when(paymentFacade).handleWebhook(rawBody, "webhook-1", verified);

        mockMvc.perform(post("/v1/webhooks/portone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("webhook-id", "webhook-1")
                        .header("webhook-timestamp", "timestamp-1")
                        .header("webhook-signature", "signature-1")
                        .content(rawBody))
                .andExpect(status().isOk());

        verify(portOneWebhookVerifier).verify(rawBody, "webhook-1", "signature-1", "timestamp-1");
        verify(paymentFacade).handleWebhook(rawBody, "webhook-1", verified);
    }

    @Test
    @DisplayName("웹훅 서명 검증 실패 시 실패 이력을 남기고 200 OK를 반환한다")
    void receiveWebhook_verificationFailHandledInController() throws Exception {
        String rawBody = "{\"type\":\"PAID\"}";

        Mockito.when(portOneWebhookVerifier.verify(rawBody, "webhook-2", "signature-2", "timestamp-2"))
                .thenThrow(new WebhookVerificationException("verify failed", new RuntimeException()));
        doNothing().when(paymentFacade).handleInvalidWebhook(rawBody, "webhook-2", "verify failed");

        mockMvc.perform(post("/v1/webhooks/portone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("webhook-id", "webhook-2")
                        .header("webhook-timestamp", "timestamp-2")
                        .header("webhook-signature", "signature-2")
                        .content(rawBody))
                .andExpect(status().isOk());

        verify(portOneWebhookVerifier).verify(rawBody, "webhook-2", "signature-2", "timestamp-2");
        verify(paymentFacade).handleInvalidWebhook(rawBody, "webhook-2", "verify failed");
        verify(paymentFacade, never()).handleWebhook(Mockito.eq(rawBody), Mockito.eq("webhook-2"), Mockito.any());
    }
}
