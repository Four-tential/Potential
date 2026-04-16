package four_tential.potential.application.payment;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.CommonExceptionEnum;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.fixture.CourseFixture;
import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.entity.Webhook;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.domain.payment.port.PaymentGateway;
import four_tential.potential.domain.payment.port.PaymentGatewayRequest;
import four_tential.potential.domain.payment.port.PaymentGatewayResponse;
import four_tential.potential.infra.portone.PortOneWebhookHandler;
import four_tential.potential.presentation.payment.dto.PaymentCreateRequest;
import four_tential.potential.presentation.payment.dto.PaymentCreateResponse;
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

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private PaymentDistributedLockExecutor paymentLockManager;

    @Mock
    private TransactionStatus transactionStatus;

    private Webhook savedWebhook;

    @BeforeEach
    void setUp() {
        savedWebhook = Webhook.receive("test-webhook-id", "UNKNOWN");
        lenient().when(webhookService.findProcessablePaidWebhook(anyString()))
                .thenReturn(Optional.empty());
        stubLocks();
    }

    @Test
    @DisplayName("결제 생성 검증 성공 시 PENDING 결제를 생성한다")
    void createPayment_success() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(
                orderId,
                "pg-key-1",
                PaymentPayWay.CARD,
                null
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-1",
                "PAID",
                100000L,
                "card"
        );
        Payment payment = Payment.createPending(
                orderId,
                memberId,
                null,
                "pg-key-1",
                100000L,
                0L,
                100000L,
                PaymentPayWay.CARD
        );

        given(paymentGateway.getPayment("pg-key-1")).willReturn(gatewayResponse);
        stubOrderAndCourse(orderId, memberId, courseId, 1);
        given(paymentService.createPendingPayment(any(PaymentCreateCommand.class), eq("pg-key-1"), eq(PaymentPayWay.CARD)))
                .willReturn(payment);
        given(paymentService.getByPgKey("pg-key-1")).willReturn(payment);

        PaymentCreateResponse response = paymentFacade.createPayment(memberId, request);

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.paidTotalPrice()).isEqualTo(100000L);
        verify(paymentLockManager).executeWithOrderLock(eq(orderId), any());
        verify(paymentService).validateNoPayment(orderId);
        verify(paymentService).validateGatewayPayment(any(PaymentCreateCommand.class), eq("pg-key-1"), eq(PaymentPayWay.CARD), eq(gatewayResponse));
        verify(paymentGateway, never()).cancelPayment(any());
    }

    @Test
    @DisplayName("결제 생성 전에 도착한 Paid 웹훅이 있으면 결제 생성 직후 이어서 결제 완료 처리한다")
    void createPayment_resumesDeferredPaidWebhook() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(
                orderId,
                "pg-key-first-webhook",
                PaymentPayWay.CARD,
                null
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-first-webhook",
                "PAID",
                100000L,
                "card"
        );
        Payment createdPayment = Payment.createPending(
                orderId,
                memberId,
                null,
                "pg-key-first-webhook",
                100000L,
                0L,
                100000L,
                PaymentPayWay.CARD
        );
        Payment paymentForWebhook = Payment.createPending(
                orderId,
                memberId,
                null,
                "pg-key-first-webhook",
                100000L,
                0L,
                100000L,
                PaymentPayWay.CARD
        );
        Payment latestPayment = Payment.createPending(
                orderId,
                memberId,
                null,
                "pg-key-first-webhook",
                100000L,
                0L,
                100000L,
                PaymentPayWay.CARD
        );
        latestPayment.confirmPaid();
        Webhook deferredWebhook = Webhook.receive("webhook-first", "WebhookTransactionPaid");
        deferredWebhook.updatePgKey("pg-key-first-webhook");

        given(paymentGateway.getPayment("pg-key-first-webhook")).willReturn(gatewayResponse);
        stubOrderAndCourse(orderId, memberId, courseId, 1);
        given(paymentService.createPendingPayment(any(PaymentCreateCommand.class), eq("pg-key-first-webhook"), eq(PaymentPayWay.CARD)))
                .willReturn(createdPayment);
        given(webhookService.findProcessablePaidWebhook("pg-key-first-webhook"))
                .willReturn(Optional.of(deferredWebhook));
        given(webhookService.retry(deferredWebhook, "WebhookTransactionPaid"))
                .willReturn(deferredWebhook);
        given(paymentService.getByPgKey("pg-key-first-webhook"))
                .willReturn(createdPayment)
                .willReturn(latestPayment);
        given(paymentService.getByPgKeyForUpdate("pg-key-first-webhook")).willReturn(paymentForWebhook);
        given(transactionTemplate.execute(any(TransactionCallback.class)))
                .willAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(transactionStatus);
                });

        PaymentCreateResponse response = paymentFacade.createPayment(memberId, request);

        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        verify(paymentService).confirmPaid(paymentForWebhook);
        verify(webhookService).complete(deferredWebhook);
        verify(webhookService, never()).fail(deferredWebhook);
    }

    @Test
    @DisplayName("결제 생성 검증 실패 시 PortOne 결제 취소를 요청한다")
    void createPayment_validationFail_cancelGatewayPayment() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(
                orderId,
                "pg-key-2",
                PaymentPayWay.CARD,
                null
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-2",
                "PAID",
                90000L,
                "card"
        );

        given(paymentGateway.getPayment("pg-key-2")).willReturn(gatewayResponse);
        stubOrderAndCourse(orderId, memberId, courseId, 1);
        doThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_AMOUNT_MISMATCH))
                .when(paymentService)
                .validateGatewayPayment(any(PaymentCreateCommand.class), eq("pg-key-2"), eq(PaymentPayWay.CARD), eq(gatewayResponse));

        assertThatThrownBy(() -> paymentFacade.createPayment(memberId, request))
                .isInstanceOf(ServiceErrorException.class);

        verify(paymentGateway).cancelPayment(PaymentGatewayRequest.of(
                "pg-key-2",
                90000L,
                "PAYMENT_CREATE_REJECTED"
        ));
    }

    @Test
    @DisplayName("락 획득 실패 같은 인프라 예외는 PortOne 결제 취소로 연결하지 않는다")
    void createPayment_infraFail_doesNotCancelGatewayPayment() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(
                orderId,
                "pg-key-lock-fail",
                PaymentPayWay.CARD,
                null
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-lock-fail",
                "PAID",
                100000L,
                "card"
        );

        given(paymentGateway.getPayment("pg-key-lock-fail")).willReturn(gatewayResponse);
        given(paymentLockManager.executeWithOrderLock(eq(orderId), any()))
                .willThrow(new ServiceErrorException(CommonExceptionEnum.ERR_GET_DISTRIBUTED_LOCK_FAIL));

        assertThatThrownBy(() -> paymentFacade.createPayment(memberId, request))
                .isInstanceOf(ServiceErrorException.class);

        verify(paymentGateway, never()).cancelPayment(any());
    }

    @Test
    @DisplayName("완료된 중복 웹훅이면 이후 로직을 수행하지 않는다")
    void handleWebhook_duplicate() throws Exception {
        given(webhookService.isCompleted("test-webhook-id")).willReturn(true);

        paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature");

        verify(webhookService).isCompleted("test-webhook-id");
        verify(webhookService, never()).receive(any(), any());
        verify(portOneWebhookHandler, never()).verify(any(), any(), any(), any());
        verify(transactionTemplate, never()).execute(any(TransactionCallback.class));
        verify(webhookService, never()).complete(any());
        verify(webhookService, never()).fail(any());
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("서명 검증 실패 시 WebhookVerificationException 을 던지고 웹훅 실패 상태를 저장한다")
    void handleWebhook_verificationFail() throws Exception {
        given(webhookService.isCompleted("test-webhook-id")).willReturn(false);
        stubReceiveTransaction();
        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willThrow(new WebhookVerificationException("verify failed", new RuntimeException()));

        assertThatThrownBy(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .isInstanceOf(WebhookVerificationException.class);

        verify(webhookService).receive("test-webhook-id", "UNKNOWN");
        verify(webhookService, never()).complete(any());
        verify(webhookService).fail(savedWebhook);
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("결제 완료 웹훅이면 결제 완료 처리 후 webhook 완료 상태로 저장한다")
    void handleWebhook_paid() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Order order = createOrder(orderId, memberId, courseId, 1);
        Course course = createCourse(courseId);
        Payment payment = Payment.createPending(
                orderId,
                memberId,
                null,
                "payment-1",
                100000L,
                0L,
                100000L,
                PaymentPayWay.CARD
        );

        stubSuccessTransactionFlow();
        given(paymentService.findByPgKey("payment-1")).willReturn(Optional.of(payment));
        given(paymentService.getByPgKeyForUpdate("payment-1")).willReturn(payment);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-1"));

        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(paymentService).confirmPaid(payment);
        verify(paymentService, never()).fail(any(Payment.class));
        verify(webhookService).complete(savedWebhook);
        verify(webhookService, never()).fail(savedWebhook);
        assertThat(course.getConfirmCount()).isEqualTo(1);

        Object eventStatus = ReflectionTestUtils.getField(savedWebhook, "eventStatus");
        if (eventStatus != null) {
            assertThat(eventStatus).isEqualTo("WebhookTransactionPaid");
        }
    }

    @Test
    @DisplayName("결제 완료 웹훅이 먼저 왔지만 결제가 아직 저장되지 않았으면 실패가 아니라 대기 상태로 남긴다")
    void handleWebhook_paid_beforePaymentSaved_defer() throws Exception {
        given(webhookService.isCompleted("test-webhook-id")).willReturn(false);
        given(webhookService.receive("test-webhook-id", "UNKNOWN")).willReturn(savedWebhook);
        given(paymentService.findByPgKey("payment-not-yet-saved"))
                .willReturn(Optional.empty());
        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-not-yet-saved"));

        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(paymentService).findByPgKey("payment-not-yet-saved");
        verify(webhookService).defer(savedWebhook, "WebhookTransactionPaid", "payment-not-yet-saved");
        verify(paymentService, never()).confirmPaid(any());
        verify(webhookService, never()).complete(savedWebhook);
        verify(webhookService, never()).fail(savedWebhook);
    }

    @Test
    @DisplayName("결제 실패 웹훅이면 결제 실패 처리 후 webhook 완료 상태로 저장한다")
    void handleWebhook_failed() throws Exception {
        Payment payment = createPayment("payment-2");
        stubSuccessTransactionFlow();
        given(paymentService.getByPgKeyForUpdate("payment-2")).willReturn(payment);
        WebhookTransactionFailed verified =
                new WebhookTransactionFailed(new TestWebhookTransactionData("payment-2"));

        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(paymentService).fail(payment);
        verify(paymentService, never()).confirmPaid(any());
        verify(webhookService).complete(savedWebhook);
        verify(webhookService, never()).fail(savedWebhook);
    }

    @Test
    @DisplayName("결제 취소 웹훅이면 결제 실패 처리 후 webhook 완료 상태로 저장한다")
    void handleWebhook_cancelled() throws Exception {
        Payment payment = createPayment("payment-3");
        stubSuccessTransactionFlow();
        given(paymentService.getByPgKeyForUpdate("payment-3")).willReturn(payment);
        WebhookTransactionCancelled verified =
                new WebhookTransactionCancelled(new TestWebhookTransactionData("payment-3"));

        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(paymentService).fail(payment);
        verify(paymentService, never()).confirmPaid(any());
        verify(webhookService).complete(savedWebhook);
        verify(webhookService, never()).fail(savedWebhook);
    }

    @Test
    @DisplayName("처리하지 않는 트랜잭션 타입이면 결제 서비스 호출 없이 webhook 완료 상태만 저장한다")
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
        verify(paymentService, never()).fail(any(Payment.class));
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
        verify(paymentService, never()).fail(any(Payment.class));
        verify(webhookService).complete(savedWebhook);
        verify(webhookService, never()).fail(savedWebhook);
    }

    @Test
    @DisplayName("비즈니스 처리 중 예외가 발생하면 webhook 실패 상태 저장 후 예외를 다시 던진다")
    void handleWebhook_businessFail() throws Exception {
        given(webhookService.isCompleted("test-webhook-id")).willReturn(false);

        given(transactionTemplate.execute(any(TransactionCallback.class)))
                .willThrow(new RuntimeException("business failed"));

        given(webhookService.receive("test-webhook-id", "UNKNOWN")).willReturn(savedWebhook);

        WebhookTransactionFailed verified =
                new WebhookTransactionFailed(new TestWebhookTransactionData("payment-5"));

        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatThrownBy(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("business failed");

        verify(webhookService).fail(savedWebhook);
        verify(webhookService, never()).complete(savedWebhook);
    }

    private void stubLocks() {
        lenient().when(paymentLockManager.executeWithOrderLock(any(UUID.class), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
        lenient().when(paymentLockManager.executeWithCourseLock(any(UUID.class), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
        lenient().when(paymentLockManager.executeWithPgKeyLock(any(String.class), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
    }

    private void stubReceiveTransaction() {
        given(webhookService.receive("test-webhook-id", "UNKNOWN")).willReturn(savedWebhook);
    }

    private void stubSuccessTransactionFlow() {
        given(webhookService.isCompleted("test-webhook-id")).willReturn(false);

        given(transactionTemplate.execute(any(TransactionCallback.class)))
                .willAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(transactionStatus);
                });

        given(webhookService.receive("test-webhook-id", "UNKNOWN")).willReturn(savedWebhook);
    }

    private void stubOrderAndCourse(UUID orderId, UUID memberId, UUID courseId, int orderCount) {
        Order order = createOrder(orderId, memberId, courseId, orderCount);
        Course course = createCourse(courseId);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
    }

    private Order createOrder(UUID orderId, UUID memberId, UUID courseId, int orderCount) {
        Order order = Order.register(
                memberId,
                courseId,
                orderCount,
                BigInteger.valueOf(100000),
                "test course"
        );
        ReflectionTestUtils.setField(order, "id", orderId);
        return order;
    }

    private Course createCourse(UUID courseId) {
        Course course = CourseFixture.defaultCourse();
        ReflectionTestUtils.setField(course, "id", courseId);
        return course;
    }

    private Payment createPayment(String pgKey) {
        return Payment.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                pgKey,
                100000L,
                0L,
                100000L,
                PaymentPayWay.CARD
        );
    }

    private static class PlainWebhook implements io.portone.sdk.server.webhook.Webhook {
    }

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
