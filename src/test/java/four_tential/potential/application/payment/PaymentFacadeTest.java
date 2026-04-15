package four_tential.potential.application.payment;

import four_tential.potential.domain.payment.entity.Webhook;
import four_tential.potential.domain.payment.port.PaymentGateway;
import four_tential.potential.infra.portone.PortOneWebhookHandler;
import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.WebhookTransaction;
import io.portone.sdk.server.webhook.WebhookTransactionData;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private TransactionStatus transactionStatus;

    private Webhook savedWebhook;

    @BeforeEach
    void setUp() {
        savedWebhook = Webhook.receive("test-webhook-id", "UNKNOWN");
    }

    @Test
    @DisplayName("중복 웹훅이면 이후 로직을 수행하지 않는다")
    void handleWebhook_duplicate() throws Exception {
        given(webhookService.isDuplicate("test-webhook-id")).willReturn(true);

        paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature");

        verify(webhookService).isDuplicate("test-webhook-id");
        verify(webhookService, never()).receive(any(), any());
        verify(portOneWebhookHandler, never()).verify(any(), any(), any(), any());
        verify(transactionTemplate, never()).execute(any(TransactionCallback.class));
        verify(webhookService, never()).complete(any());
        verify(webhookService, never()).fail(any());
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("서명 검증 실패 시 WebhookVerificationException을 던지고 완료/실패 처리는 하지 않는다")
    void handleWebhook_verificationFail() throws Exception {
        given(webhookService.isDuplicate("test-webhook-id")).willReturn(false);
        stubReceiveTransaction();
        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willThrow(new WebhookVerificationException("검증 실패", new RuntimeException()));

        assertThatThrownBy(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .isInstanceOf(WebhookVerificationException.class);

        verify(webhookService).receive("test-webhook-id", "UNKNOWN");
        verify(webhookService, never()).complete(any());
        verify(webhookService, never()).fail(any());
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("결제 완료 웹훅이면 결제 완료 처리 후 webhook 완료 상태로 저장한다")
    void handleWebhook_paid() throws Exception {
        stubSuccessTransactionFlow();
        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-1"));

        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(paymentService).confirmPaid("payment-1");
        verify(paymentService, never()).fail(any());
        verify(webhookService).complete(savedWebhook);
        verify(webhookService, never()).fail(savedWebhook);

        Object eventStatus = ReflectionTestUtils.getField(savedWebhook, "eventStatus");
        if (eventStatus != null) {
            assertThat(eventStatus).isEqualTo("WebhookTransactionPaid");
        }
    }

    @Test
    @DisplayName("결제 실패 웹훅이면 결제 실패 처리 후 webhook 완료 상태로 저장한다")
    void handleWebhook_failed() throws Exception {
        stubSuccessTransactionFlow();
        WebhookTransactionFailed verified =
                new WebhookTransactionFailed(new TestWebhookTransactionData("payment-2"));

        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(paymentService).fail("payment-2");
        verify(paymentService, never()).confirmPaid(any());
        verify(webhookService).complete(savedWebhook);
        verify(webhookService, never()).fail(savedWebhook);
    }

    @Test
    @DisplayName("결제 취소 웹훅이면 결제 실패 처리 후 webhook 완료 상태로 저장한다")
    void handleWebhook_cancelled() throws Exception {
        stubSuccessTransactionFlow();
        WebhookTransactionCancelled verified =
                new WebhookTransactionCancelled(new TestWebhookTransactionData("payment-3"));

        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(paymentService).fail("payment-3");
        verify(paymentService, never()).confirmPaid(any());
        verify(webhookService).complete(savedWebhook);
        verify(webhookService, never()).fail(savedWebhook);
    }

    @Test
    @DisplayName("처리되지 않은 트랜잭션 타입이면 결제 서비스 호출 없이 webhook 완료 상태만 저장한다")
    void handleWebhook_unknownTransactionType() throws Exception {
        stubSuccessTransactionFlow();
        WebhookTransactionUnknown verified =
                new WebhookTransactionUnknown(new TestWebhookTransactionData("payment-4"));

        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(paymentService, never()).confirmPaid(any());
        verify(paymentService, never()).fail(any());
        verify(webhookService).complete(savedWebhook);
        verify(webhookService, never()).fail(savedWebhook);
    }

    @Test
    @DisplayName("트랜잭션 타입이 아닌 웹훅이면 결제 서비스 호출 없이 webhook 완료 상태만 저장한다")
    void handleWebhook_nonTransactionWebhook() throws Exception {
        stubSuccessTransactionFlow();
        PlainWebhook verified = new PlainWebhook();

        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(paymentService, never()).confirmPaid(any());
        verify(paymentService, never()).fail(any());
        verify(webhookService).complete(savedWebhook);
        verify(webhookService, never()).fail(savedWebhook);
    }

    @Test
    @DisplayName("비즈니스 처리 중 예외가 발생하면 webhook 실패 상태 저장 후 예외를 다시 던진다")
    void handleWebhook_businessFail() throws Exception {
        given(webhookService.isDuplicate("test-webhook-id")).willReturn(false);

        given(transactionTemplate.execute(any(TransactionCallback.class)))
                .willAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(transactionStatus);
                })
                .willAnswer(invocation -> {
                    throw new RuntimeException("비즈니스 처리 실패");
                });

        given(webhookService.receive("test-webhook-id", "UNKNOWN")).willReturn(savedWebhook);

        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-5"));

        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatThrownBy(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("비즈니스 처리 실패");

        verify(webhookService).fail(savedWebhook);
        verify(webhookService, never()).complete(savedWebhook);
    }

    private void stubReceiveTransaction() {
        given(transactionTemplate.execute(any(TransactionCallback.class)))
                .willAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(transactionStatus);
                });

        given(webhookService.receive("test-webhook-id", "UNKNOWN")).willReturn(savedWebhook);
    }

    private void stubSuccessTransactionFlow() {
        given(webhookService.isDuplicate("test-webhook-id")).willReturn(false);

        given(transactionTemplate.execute(any(TransactionCallback.class)))
                .willAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(transactionStatus);
                })
                .willAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(transactionStatus);
                });

        given(webhookService.receive("test-webhook-id", "UNKNOWN")).willReturn(savedWebhook);
    }

    /**
     * PortOne SDK Webhook 인터페이스 테스트용 더미
     */
    private static class PlainWebhook implements io.portone.sdk.server.webhook.Webhook {
    }

    /**
     * PortOne SDK WebhookTransactionData 테스트용 더미
     */
    private static class TestWebhookTransactionData implements WebhookTransactionData {
        private final String paymentId;
        private final String storeId;

        private TestWebhookTransactionData(String paymentId) {
            this(paymentId, "store-1");
        }

        private TestWebhookTransactionData(String paymentId, String storeId) {
            this.paymentId = paymentId;
            this.storeId = storeId;
        }

        @Override
        public String getPaymentId() {
            return paymentId;
        }

        @Override
        public String getStoreId() {
            return storeId;
        }

        @Override
        public @NotNull String getTransactionId() {
            return "";
        }
    }

    /**
     * 반복 구현 줄이기 위한 공통 부모
     */
    private abstract static class BaseWebhookTransaction implements WebhookTransaction {
        protected final WebhookTransactionData data;

        protected BaseWebhookTransaction(WebhookTransactionData data) {
            this.data = data;
        }

        @Override
        public WebhookTransactionData getData() {
            return data;
        }

        @Override
        public Instant getTimestamp() {
            return Instant.parse("2026-04-15T00:00:00Z");
        }
    }

    /**
     * getClass().getSimpleName() 값이 PaymentFacade switch 분기명과 같아야 함
     */
    private static class WebhookTransactionPaid extends BaseWebhookTransaction {
        private WebhookTransactionPaid(WebhookTransactionData data) {
            super(data);
        }
    }

    private static class WebhookTransactionFailed extends BaseWebhookTransaction {
        private WebhookTransactionFailed(WebhookTransactionData data) {
            super(data);
        }
    }

    private static class WebhookTransactionCancelled extends BaseWebhookTransaction {
        private WebhookTransactionCancelled(WebhookTransactionData data) {
            super(data);
        }
    }

    private static class WebhookTransactionUnknown extends BaseWebhookTransaction {
        private WebhookTransactionUnknown(WebhookTransactionData data) {
            super(data);
        }
    }
}