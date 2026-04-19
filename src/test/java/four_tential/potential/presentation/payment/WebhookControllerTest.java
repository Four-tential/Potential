package four_tential.potential.presentation.payment;

import four_tential.potential.application.payment.PaymentFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookControllerTest {

    private MockMvc mockMvc;
    private PaymentFacade paymentFacade;

    @BeforeEach
    void setUp() {
        paymentFacade = Mockito.mock(PaymentFacade.class);
        WebhookController controller = new WebhookController(paymentFacade);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("웹훅 처리 성공 시 200 OK를 반환한다")
    void receiveWebhook_success() throws Exception {
        String rawBody = "{\"type\":\"PAID\"}";

        doNothing().when(paymentFacade)
                .handleWebhook(rawBody, "webhook-1", "timestamp-1", "signature-1");

        mockMvc.perform(post("/v1/webhooks/portone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("webhook-id", "webhook-1")
                        .header("webhook-timestamp", "timestamp-1")
                        .header("webhook-signature", "signature-1")
                        .content(rawBody))
                .andExpect(status().isOk());

        verify(paymentFacade)
                .handleWebhook(rawBody, "webhook-1", "timestamp-1", "signature-1");
    }

    @Test
    @DisplayName("서명 검증 실패가 Facade 내부에서 기록되면 200 OK를 반환한다")
    void receiveWebhook_verificationFailHandledInFacade() throws Exception {
        String rawBody = "{\"type\":\"PAID\"}";

        doNothing().when(paymentFacade)
                .handleWebhook(rawBody, "webhook-2", "timestamp-2", "signature-2");

        mockMvc.perform(post("/v1/webhooks/portone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("webhook-id", "webhook-2")
                        .header("webhook-timestamp", "timestamp-2")
                        .header("webhook-signature", "signature-2")
                        .content(rawBody))
                .andExpect(status().isOk());

        verify(paymentFacade)
                .handleWebhook(rawBody, "webhook-2", "timestamp-2", "signature-2");
    }
}
