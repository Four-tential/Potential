package four_tential.potential.application.payment;

import four_tential.potential.application.order.OrderService;
import four_tential.potential.application.order.WaitingListService;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.fixture.CourseFixture;
import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.entity.Webhook;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.domain.payment.port.PaymentGateway;
import four_tential.potential.domain.payment.port.PaymentGatewayRequest;
import four_tential.potential.domain.payment.port.PaymentGatewayResponse;
import four_tential.potential.presentation.payment.dto.PaymentCreateRequest;
import four_tential.potential.presentation.payment.dto.PaymentCreateResponse;
import four_tential.potential.presentation.payment.dto.PaymentDetailResponse;
import four_tential.potential.presentation.payment.dto.PaymentListResponse;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private TransactionTemplate transactionTemplate;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private PaymentDistributedLockExecutor paymentLockExecutor;

    @Mock
    private OrderService orderService;

    @Mock
    private WaitingListService waitingListService;

    @Mock
    private PaymentMetrics paymentMetrics;

    @Mock
    private TransactionStatus transactionStatus;

    private Webhook savedWebhook;

    @BeforeEach
    void setUp() {
        savedWebhook = Webhook.createPendingRecord("test-webhook-id", "UNKNOWN", null);

        lenient().when(webhookService.isFinished(anyString())).thenReturn(false);
        lenient().when(webhookService.saveIncomingWebhook(anyString(), anyString(), any()))
                .thenReturn(savedWebhook);
        lenient().when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(transactionStatus);
                });
        lenient().when(webhookService.merge(any(Webhook.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        stubLocks();
    }

    @Test
    @DisplayName("createPayment는 서버에서 생성한 pgKey로 PENDING 결제를 준비한다")
    void createPayment_success() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(orderId, PaymentPayWay.CARD);
        Order order = createOrder(orderId, memberId, courseId, 1);
        Course course = createCourse(courseId);
        Payment createdPayment = Payment.createPending(
                orderId,
                memberId,
                "pservergeneratedkey1234567890",
                100000L,
                100000L,
                PaymentPayWay.CARD
        );

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(paymentService.findByOrderId(orderId)).willReturn(Optional.empty());
        given(paymentService.findByPgKey(anyString())).willReturn(Optional.empty());
        given(paymentService.createPendingPayment(any(PaymentCreateCommand.class), anyString(), eq(PaymentPayWay.CARD)))
                .willReturn(createdPayment);

        PaymentCreateResponse response = paymentFacade.createPayment(memberId, request);

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.pgKey()).isEqualTo("pservergeneratedkey1234567890");
        verify(paymentService).validateNoPayment(orderId);
        verify(paymentService).createPendingPayment(any(PaymentCreateCommand.class), anyString(), eq(PaymentPayWay.CARD));
        verify(paymentGateway, never()).getPayment(anyString());
        verify(paymentGateway, never()).cancelPayment(any());
    }

    @Test
    @DisplayName("createPayment는 기존 PENDING 결제가 있으면 그 결제를 그대로 반환한다")
    void createPayment_returnsExistingPendingPayment() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(orderId, PaymentPayWay.CARD);
        Order order = createOrder(orderId, memberId, courseId, 1);
        Payment existingPayment = Payment.createPending(
                orderId,
                memberId,
                "pexistingpendingkey",
                100000L,
                100000L,
                PaymentPayWay.CARD
        );

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(paymentService.findByOrderId(orderId)).willReturn(Optional.of(existingPayment));

        PaymentCreateResponse response = paymentFacade.createPayment(memberId, request);

        assertThat(response.pgKey()).isEqualTo("pexistingpendingkey");
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentService, never()).createPendingPayment(any(), anyString(), any());
        verify(paymentGateway, never()).getPayment(anyString());
    }

    @Test
    @DisplayName("createPayment는 이미 결제 완료된 주문이면 예외를 던진다")
    void createPayment_alreadyPaid_throws() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(orderId, PaymentPayWay.CARD);
        Order order = createOrder(orderId, memberId, courseId, 1);
        Payment existingPayment = createPendingPayment(orderId, memberId, "palreadypaidkey");
        existingPayment.confirmPaid();

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(paymentService.findByOrderId(orderId)).willReturn(Optional.of(existingPayment));

        assertThatThrownBy(() -> paymentFacade.createPayment(memberId, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(PaymentExceptionEnum.ERR_ALREADY_PAID.getMessage());
    }

    @Test
    @DisplayName("createPayment는 종료된 결제가 이미 있으면 예외를 던진다")
    void createPayment_alreadyRequested_throws() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(orderId, PaymentPayWay.CARD);
        Order order = createOrder(orderId, memberId, courseId, 1);
        Payment existingPayment = createPendingPayment(orderId, memberId, "palreadyrequestedkey");
        existingPayment.fail();

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(paymentService.findByOrderId(orderId)).willReturn(Optional.of(existingPayment));

        assertThatThrownBy(() -> paymentFacade.createPayment(memberId, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_ALREADY_REQUESTED.getMessage());
    }

    @Test
    @DisplayName("createPayment는 주문 회원과 요청 회원이 다르면 예외를 던진다")
    void createPayment_memberMismatch_throws() {
        UUID requestMemberId = UUID.randomUUID();
        UUID orderMemberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(orderId, PaymentPayWay.CARD);
        Order order = createOrder(orderId, orderMemberId, courseId, 1);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentFacade.createPayment(requestMemberId, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_FORBIDDEN.getMessage());

        verify(paymentGateway, never()).getPayment(anyString());
        verify(paymentGateway, never()).cancelPayment(any());
    }

    @Test
    @DisplayName("createPayment는 주문이 없으면 예외를 던진다")
    void createPayment_orderNotFound_throws() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        given(orderRepository.findById(orderId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentFacade.createPayment(memberId, new PaymentCreateRequest(orderId, PaymentPayWay.CARD)))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_NOT_FOUND_ORDER.getMessage());
    }

    @Test
    @DisplayName("createPayment는 코스가 없으면 예외를 던진다")
    void createPayment_courseNotFound_throws() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(orderId, PaymentPayWay.CARD);
        Order order = createOrder(orderId, memberId, courseId, 1);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(paymentService.findByOrderId(orderId)).willReturn(Optional.empty());
        given(courseRepository.findById(courseId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentFacade.createPayment(memberId, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_COURSE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("createPayment는 주문 상태가 PENDING이 아니면 예외를 던진다")
    void createPayment_invalidOrderStatus_throws() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(orderId, PaymentPayWay.CARD);
        Order order = createOrder(orderId, memberId, courseId, 1);
        ReflectionTestUtils.setField(order, "status", OrderStatus.CANCELLED);
        Course course = createCourse(courseId);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(paymentService.findByOrderId(orderId)).willReturn(Optional.empty());
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() -> paymentFacade.createPayment(memberId, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_INVALID_ORDER_STATUS.getMessage());
    }

    @Test
    @DisplayName("createPayment는 주문이 만료되었으면 예외를 던진다")
    void createPayment_expiredOrder_throws() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(orderId, PaymentPayWay.CARD);
        Order order = createOrder(orderId, memberId, courseId, 1);
        ReflectionTestUtils.setField(order, "expireAt", LocalDateTime.now().minusMinutes(1));
        Course course = createCourse(courseId);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(paymentService.findByOrderId(orderId)).willReturn(Optional.empty());
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() -> paymentFacade.createPayment(memberId, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_DEADLINE_EXCEEDED.getMessage());
    }

    @Test
    @DisplayName("createPayment는 남은 좌석이 없으면 예외를 던진다")
    void createPayment_noAvailableSeats_throws() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(orderId, PaymentPayWay.CARD);
        Order order = createOrder(orderId, memberId, courseId, 1);
        Course course = createCourse(courseId);
        ReflectionTestUtils.setField(course, "capacity", 1);
        ReflectionTestUtils.setField(course, "confirmCount", 1);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(paymentService.findByOrderId(orderId)).willReturn(Optional.empty());
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() -> paymentFacade.createPayment(memberId, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_NO_AVAILABLE_SEATS.getMessage());
    }

    @Test
    @DisplayName("createPayment는 금액을 long으로 변환할 수 없으면 예외를 던진다")
    void createPayment_amountOverflow_throws() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(orderId, PaymentPayWay.CARD);
        Order order = createOrder(orderId, memberId, courseId, 1);
        Course course = createCourse(courseId);
        ReflectionTestUtils.setField(order, "totalPriceSnap", BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(paymentService.findByOrderId(orderId)).willReturn(Optional.empty());
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() -> paymentFacade.createPayment(memberId, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_AMOUNT_MISMATCH.getMessage());
    }

    @Test
    @DisplayName("createPayment는 생성한 모든 pgKey가 충돌하면 실패한다")
    void createPayment_pgKeyGenerationFail_throws() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(orderId, PaymentPayWay.CARD);
        Order order = createOrder(orderId, memberId, courseId, 1);
        Course course = createCourse(courseId);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(paymentService.findByOrderId(orderId)).willReturn(Optional.empty());
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(paymentService.findByPgKey(anyString()))
                .willReturn(Optional.of(createPendingPayment(orderId, memberId, "duplicate")));

        assertThatThrownBy(() -> paymentFacade.createPayment(memberId, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_KEY_GENERATION_FAILED.getMessage());
    }

    @Test
    @DisplayName("handleInvalidWebhook는 실패한 웹훅 기록을 저장한다")
    void handleInvalidWebhook_recordsFailedWebhook() {
        paymentFacade.handleInvalidWebhook("{}", "invalid-webhook-id", "signature mismatch");

        verify(webhookService).failWebhook(
                eq(savedWebhook),
                eq("WEBHOOK_SIGNATURE_INVALID"),
                eq("signature mismatch")
        );
    }

    @Test
    @DisplayName("이미 처리 완료된 웹훅은 수신 전에 무시한다")
    void handleWebhook_finishedWebhookIgnoredBeforeReceive() throws Exception {
        given(webhookService.isFinished("finished-webhook-id")).willReturn(true);

        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-finished"));

        paymentFacade.handleWebhook("{}", "finished-webhook-id", verified);

        verify(webhookService, never()).saveIncomingWebhook(anyString(), anyString(), any());
        verify(webhookService, never()).completeWebhook(any());
        verify(webhookService, never()).failWebhook(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("재조회 후 이미 완료된 웹훅은 무시한다")
    void handleWebhook_finishedWebhookIgnoredAfterReceive() throws Exception {
        Webhook finishedWebhook = Webhook.createPendingRecord("finished-after-receive", "UNKNOWN", null);
        finishedWebhook.markCompleted();
        given(webhookService.saveIncomingWebhook("finished-after-receive", "UNKNOWN", "{}"))
                .willReturn(finishedWebhook);

        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-finished-after-receive"));

        paymentFacade.handleWebhook("{}", "finished-after-receive", verified);

        verify(webhookService, never()).completeWebhook(any());
        verify(webhookService, never()).failWebhook(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("paid 웹훅은 결제를 한 번만 확정한다")
    void handleWebhook_paid() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Order order = createOrder(orderId, memberId, courseId, 1);
        Course course = createCourse(courseId);
        Payment payment = createPendingPayment(orderId, memberId, "payment-1");

        given(paymentService.findByPgKey("payment-1")).willReturn(Optional.of(payment));
        given(paymentService.getByPgKeyForUpdate("payment-1")).willReturn(payment);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-1"));

        assertThatCode(() -> paymentFacade.handleWebhook("{}", "test-webhook-id", verified))
                .doesNotThrowAnyException();

        verify(paymentService).confirmPaid(payment);
        verify(orderService).completePayment(orderId);
        verify(waitingListService).completeOccupyingSeat(courseId, memberId);
        verify(webhookService).completeWebhook(savedWebhook);
        verify(paymentGateway, never()).cancelPayment(any());
    }

    @Test
    @DisplayName("결제 행이 없는 paid 웹훅은 PortOne 취소를 요청한다")
    void handleWebhook_paid_withoutPaymentRow_cancelsGatewayPayment() throws Exception {
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "payment-not-found",
                "PAID",
                100000L,
                "card"
        );
        given(paymentService.findByPgKey("payment-not-found")).willReturn(Optional.empty());
        given(paymentGateway.getPayment("payment-not-found")).willReturn(gatewayResponse);

        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-not-found"));

        assertThatCode(() -> paymentFacade.handleWebhook("{}", "test-webhook-id", verified))
                .doesNotThrowAnyException();

        verify(paymentGateway).getPayment("payment-not-found");
        verify(paymentGateway).cancelPayment(PaymentGatewayRequest.of(
                "payment-not-found",
                100000L,
                "PAYMENT_NOT_FOUND"
        ));
        verify(webhookService).failWebhook(eq(savedWebhook), eq("PAYMENT_NOT_FOUND"), anyString());
        assertThat(savedWebhook.getPgKey()).isEqualTo("payment-not-found");
        assertThat(savedWebhook.getEventStatus()).isEqualTo("WebhookTransactionPaid");
    }

    @Test
    @DisplayName("서비스 예외가 발생하면 웹훅을 실패 처리하고 예외를 다시 던지지 않는다")
    void handleWebhook_businessServiceError_recordsFailedWebhook() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Payment payment = createPendingPayment(orderId, memberId, "payment-service-error");
        Order order = createOrder(orderId, memberId, courseId, 1);

        given(paymentService.findByPgKey("payment-service-error")).willReturn(Optional.of(payment));
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(paymentService.getByPgKeyForUpdate("payment-service-error"))
                .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT));

        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-service-error"));

        assertThatCode(() -> paymentFacade.handleWebhook("{}", "test-webhook-id", verified))
                .doesNotThrowAnyException();

        verify(webhookService).failWebhook(
                eq(savedWebhook),
                eq("WEBHOOK_BUSINESS_FAILED"),
                anyString()
        );
    }

    @Test
    @DisplayName("예상치 못한 런타임 예외가 발생하면 웹훅을 실패 처리하고 예외를 다시 던진다")
    void handleWebhook_businessRuntimeError_marksFailedAndRethrows() throws Exception {
        given(paymentService.findByPgKey("payment-runtime-error")).willReturn(Optional.empty());
        given(paymentGateway.getPayment("payment-runtime-error"))
                .willThrow(new RuntimeException("gateway boom"));

        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-runtime-error"));

        assertThatThrownBy(() -> paymentFacade.handleWebhook("{}", "test-webhook-id", verified))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("gateway boom");

        verify(webhookService).failWebhook(
                eq(savedWebhook),
                eq("WEBHOOK_UNEXPECTED_ERROR"),
                eq("gateway boom")
        );
    }

    @Test
    @DisplayName("PortOne 취소 실패는 웹훅 실패로 저장한다")
    void handleWebhook_cancelGatewayFail_marksWebhookFailed() throws Exception {
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "payment-cancel-fail",
                "PAID",
                100000L,
                "card"
        );
        given(paymentService.findByPgKey("payment-cancel-fail")).willReturn(Optional.empty());
        given(paymentGateway.getPayment("payment-cancel-fail")).willReturn(gatewayResponse);
        doThrow(new RuntimeException("cancel failed"))
                .when(paymentGateway)
                .cancelPayment(any(PaymentGatewayRequest.class));

        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-cancel-fail"));

        assertThatCode(() -> paymentFacade.handleWebhook("{}", "test-webhook-id", verified))
                .doesNotThrowAnyException();

        verify(webhookService).failWebhook(
                eq(savedWebhook),
                eq("PORTONE_CANCEL_FAILED"),
                eq("cancel failed")
        );
    }

    @Test
    @DisplayName("이미 결제 완료된 결제는 멱등하게 처리한다")
    void handleWebhook_paid_alreadyPaid() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Payment payment = createPendingPayment(orderId, memberId, "payment-already-paid");
        payment.confirmPaid();

        given(paymentService.findByPgKey("payment-already-paid")).willReturn(Optional.of(payment));
        given(paymentService.getByPgKeyForUpdate("payment-already-paid")).willReturn(payment);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(createOrder(orderId, memberId, courseId, 1)));

        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-already-paid"));

        paymentFacade.handleWebhook("{}", "test-webhook-id", verified);

        verify(paymentService, never()).confirmPaid(any());
        verify(paymentGateway, never()).cancelPayment(any());
        verify(webhookService).completeWebhook(savedWebhook);
    }

    @Test
    @DisplayName("회원 정보가 다르면 결제를 실패 처리하고 취소한다")
    void handleWebhook_paid_memberMismatch_cancelGatewayPayment() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID paymentMemberId = UUID.randomUUID();
        UUID orderMemberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Payment payment = createPendingPayment(orderId, paymentMemberId, "payment-member-mismatch");
        Order order = createOrder(orderId, orderMemberId, courseId, 1);
        Course course = createCourse(courseId);

        given(paymentService.findByPgKey("payment-member-mismatch")).willReturn(Optional.of(payment));
        given(paymentService.getByPgKeyForUpdate("payment-member-mismatch")).willReturn(payment);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-member-mismatch"));

        paymentFacade.handleWebhook("{}", "test-webhook-id", verified);

        verify(paymentService).fail(payment);
        verify(paymentGateway).cancelPayment(PaymentGatewayRequest.of(
                "payment-member-mismatch",
                100000L,
                "PAYMENT_ORDER_MEMBER_MISMATCH"
        ));
        verify(webhookService).failWebhook(eq(savedWebhook), eq("PAYMENT_ORDER_MEMBER_MISMATCH"), anyString());
    }

    @Test
    @DisplayName("PENDING이 아닌 결제에 paid 웹훅이 오면 결제를 취소한다")
    void handleWebhook_paid_invalidPaymentStatus_cancelGatewayPayment() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Payment payment = createPendingPayment(orderId, memberId, "payment-invalid-status");
        payment.fail();

        given(paymentService.findByPgKey("payment-invalid-status")).willReturn(Optional.of(payment));
        given(paymentService.getByPgKeyForUpdate("payment-invalid-status")).willReturn(payment);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(createOrder(orderId, memberId, courseId, 1)));

        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-invalid-status"));

        paymentFacade.handleWebhook("{}", "test-webhook-id", verified);

        verify(paymentGateway).cancelPayment(PaymentGatewayRequest.of(
                "payment-invalid-status",
                100000L,
                "PAYMENT_STATUS_INVALID"
        ));
        verify(webhookService).failWebhook(eq(savedWebhook), eq("PAYMENT_STATUS_INVALID"), anyString());
    }

    @Test
    @DisplayName("주문 상태가 유효하지 않으면 결제를 실패 처리하고 취소한다")
    void handleWebhook_paid_invalidOrderStatus_cancelGatewayPayment() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Payment payment = createPendingPayment(orderId, memberId, "payment-order-invalid");
        Order order = createOrder(orderId, memberId, courseId, 1);
        Course course = createCourse(courseId);
        ReflectionTestUtils.setField(order, "status", OrderStatus.CANCELLED);

        given(paymentService.findByPgKey("payment-order-invalid")).willReturn(Optional.of(payment));
        given(paymentService.getByPgKeyForUpdate("payment-order-invalid")).willReturn(payment);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-order-invalid"));

        paymentFacade.handleWebhook("{}", "test-webhook-id", verified);

        verify(paymentService).fail(payment);
        verify(paymentGateway).cancelPayment(PaymentGatewayRequest.of(
                "payment-order-invalid",
                100000L,
                "ORDER_STATUS_INVALID"
        ));
        verify(webhookService).failWebhook(eq(savedWebhook), eq("ORDER_STATUS_INVALID"), anyString());
    }

    @Test
    @DisplayName("만료된 주문은 결제를 실패 처리하고 취소한다")
    void handleWebhook_paid_expiredOrder_cancelGatewayPayment() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Payment payment = createPendingPayment(orderId, memberId, "payment-expired-order");
        Order order = createOrder(orderId, memberId, courseId, 1);
        Course course = createCourse(courseId);
        ReflectionTestUtils.setField(order, "expireAt", LocalDateTime.now().minusMinutes(1));

        given(paymentService.findByPgKey("payment-expired-order")).willReturn(Optional.of(payment));
        given(paymentService.getByPgKeyForUpdate("payment-expired-order")).willReturn(payment);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-expired-order"));

        paymentFacade.handleWebhook("{}", "test-webhook-id", verified);

        verify(paymentService).fail(payment);
        verify(paymentGateway).cancelPayment(PaymentGatewayRequest.of(
                "payment-expired-order",
                100000L,
                "PAYMENT_DEADLINE_EXCEEDED"
        ));
        verify(webhookService).failWebhook(eq(savedWebhook), eq("PAYMENT_DEADLINE_EXCEEDED"), anyString());
    }

    @Test
    @DisplayName("남은 좌석이 없으면 결제를 실패 처리하고 취소한다")
    void handleWebhook_paid_noAvailableSeats_cancelGatewayPayment() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Payment payment = createPendingPayment(orderId, memberId, "payment-no-seats");
        Order order = createOrder(orderId, memberId, courseId, 1);
        Course course = createCourse(courseId);
        ReflectionTestUtils.setField(course, "capacity", 1);
        ReflectionTestUtils.setField(course, "confirmCount", 1);

        given(paymentService.findByPgKey("payment-no-seats")).willReturn(Optional.of(payment));
        given(paymentService.getByPgKeyForUpdate("payment-no-seats")).willReturn(payment);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-no-seats"));

        paymentFacade.handleWebhook("{}", "test-webhook-id", verified);

        verify(paymentService).fail(payment);
        verify(paymentGateway).cancelPayment(PaymentGatewayRequest.of(
                "payment-no-seats",
                100000L,
                "NO_AVAILABLE_SEATS"
        ));
        verify(webhookService).failWebhook(eq(savedWebhook), eq("NO_AVAILABLE_SEATS"), anyString());
    }

    @Test
    @DisplayName("Paid 웹훅 처리 중 course lock 안에서 좌석이 사라지면 결제를 취소한다")
    void handleWebhook_paid_seatsDisappearInsideCourseLock_cancelGatewayPayment() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Payment payment = createPendingPayment(orderId, memberId, "payment-seats-race");
        Order order = createOrder(orderId, memberId, courseId, 1);
        Course course = createCourse(courseId);
        ReflectionTestUtils.setField(course, "capacity", 2);
        ReflectionTestUtils.setField(course, "confirmCount", 0);

        given(paymentService.findByPgKey("payment-seats-race")).willReturn(Optional.of(payment));
        given(paymentService.getByPgKeyForUpdate("payment-seats-race")).willReturn(payment);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        when(paymentLockExecutor.executeWithCourseLock(eq(courseId), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get())
                .thenReturn(false);

        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-seats-race"));

        paymentFacade.handleWebhook("{}", "test-webhook-id", verified);

        verify(paymentService).fail(payment);
        verify(paymentService, never()).confirmPaid(any());
        verify(orderService, never()).completePayment(any());
        verify(paymentGateway).cancelPayment(PaymentGatewayRequest.of(
                "payment-seats-race",
                100000L,
                "NO_AVAILABLE_SEATS"
        ));
        verify(webhookService).failWebhook(eq(savedWebhook), eq("NO_AVAILABLE_SEATS"), anyString());
    }

    @Test
    @DisplayName("failed 웹훅은 기존 결제를 실패 처리한다")
    void handleWebhook_failed() throws Exception {
        Payment payment = createPendingPayment(UUID.randomUUID(), UUID.randomUUID(), "payment-2");

        given(paymentService.findByPgKeyForUpdate("payment-2")).willReturn(Optional.of(payment));

        WebhookTransactionFailed verified =
                new WebhookTransactionFailed(new TestWebhookTransactionData("payment-2"));

        paymentFacade.handleWebhook("{}", "test-webhook-id", verified);

        verify(paymentService).fail(payment);
        verify(webhookService).completeWebhook(savedWebhook);
    }

    @Test
    @DisplayName("결제 행이 없는 failed 웹훅은 웹훅만 완료 처리한다")
    void handleWebhook_failedWithoutPayment_completesWebhookOnly() throws Exception {
        given(paymentService.findByPgKeyForUpdate("payment-missing")).willReturn(Optional.empty());

        WebhookTransactionFailed verified =
                new WebhookTransactionFailed(new TestWebhookTransactionData("payment-missing"));

        paymentFacade.handleWebhook("{}", "test-webhook-id", verified);

        verify(paymentService, never()).fail(any());
        verify(paymentGateway, never()).cancelPayment(any());
        verify(webhookService).completeWebhook(savedWebhook);
    }

    @Test
    @DisplayName("환불 완료 웹훅은 웹훅만 완료 처리한다")
    void handleWebhook_refundCompletedEvent_completesWebhookOnly() throws Exception {
        WebhookTransactionCancelledData verified =
                new WebhookTransactionCancelledData(new TestWebhookTransactionData("payment-refund-complete"));

        paymentFacade.handleWebhook("{}", "test-webhook-id", verified);

        verify(paymentService, never()).confirmPaid(any());
        verify(paymentService, never()).fail(any());
        verify(webhookService).completeWebhook(savedWebhook);
    }

    @Test
    @DisplayName("부분 환불 완료 웹훅은 웹훅만 완료 처리한다")
    void handleWebhook_partialRefundCompletedEvent_completesWebhookOnly() throws Exception {
        WebhookTransactionCancelledDataPartialCancelled verified =
                new WebhookTransactionCancelledDataPartialCancelled(new TestWebhookTransactionData("payment-partial-refund-complete"));

        paymentFacade.handleWebhook("{}", "test-webhook-id", verified);

        verify(paymentService, never()).confirmPaid(any());
        verify(paymentService, never()).fail(any());
        verify(webhookService).completeWebhook(savedWebhook);
    }

    @Test
    @DisplayName("지원하지 않는 거래 타입은 웹훅만 완료 처리한다")
    void handleWebhook_unknownTransactionType() throws Exception {
        WebhookTransactionUnknown verified =
                new WebhookTransactionUnknown(new TestWebhookTransactionData("payment-unknown"));

        paymentFacade.handleWebhook("{}", "test-webhook-id", verified);

        verify(paymentService, never()).confirmPaid(any());
        verify(paymentService, never()).fail(any());
        verify(webhookService).completeWebhook(savedWebhook);
    }

    @Test
    @DisplayName("트랜잭션이 아닌 웹훅은 이벤트 상태만 갱신한다")
    void handleWebhook_nonTransactionWebhook() throws Exception {
        PlainWebhook verified = new PlainWebhook();

        paymentFacade.handleWebhook("{}", "test-webhook-id", verified);

        verify(webhookService).updateEventStatus(savedWebhook, "PlainWebhook");
        verify(webhookService).completeWebhook(savedWebhook);
    }

    @Test
    @DisplayName("좌석 점유 정리 실패가 발생해도 결제 확정은 계속 진행된다")
    void handleWebhook_paid_occupancyCleanupFail_stillCompletesWebhook() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Order order = createOrder(orderId, memberId, courseId, 1);
        Course course = createCourse(courseId);
        Payment payment = createPendingPayment(orderId, memberId, "payment-occupancy-cleanup-fail");

        given(paymentService.findByPgKey("payment-occupancy-cleanup-fail")).willReturn(Optional.of(payment));
        given(paymentService.getByPgKeyForUpdate("payment-occupancy-cleanup-fail")).willReturn(payment);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        doThrow(new RuntimeException("redis failed"))
                .when(waitingListService)
                .completeOccupyingSeat(courseId, memberId);

        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-occupancy-cleanup-fail"));

        assertThatCode(() -> paymentFacade.handleWebhook("{}", "test-webhook-id", verified))
                .doesNotThrowAnyException();

        verify(paymentService).confirmPaid(payment);
        verify(orderService).completePayment(orderId);
        verify(waitingListService).completeOccupyingSeat(courseId, memberId);
        verify(webhookService).completeWebhook(savedWebhook);
    }

    @Test
    @DisplayName("getMyPayment는 PaymentService에 위임한다")
    void getMyPayment_returns_detail_response() {
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentDetailResponse expected = new PaymentDetailResponse(
                paymentId,
                orderId,
                "Pilates Intro Class",
                5,
                125000L,
                125000L,
                PaymentPayWay.CARD,
                PaymentStatus.PAID,
                LocalDateTime.of(2025, 1, 1, 10, 0)
        );
        given(paymentService.getMyPayment(paymentId, memberId)).willReturn(expected);

        PaymentDetailResponse result = paymentFacade.getMyPayment(memberId, paymentId);

        assertThat(result).isEqualTo(expected);
        verify(paymentService).getMyPayment(paymentId, memberId);
    }

    @Test
    @DisplayName("getAllMyPayments는 결과를 PageResponse로 감싸서 반환한다")
    void getMyPayments_returns_page_response() {
        UUID memberId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        PaymentListResponse item = new PaymentListResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Pilates Intro Class",
                5,
                125000L,
                PaymentStatus.PAID,
                LocalDateTime.of(2025, 1, 1, 10, 0)
        );
        PageResponse<PaymentListResponse> pageResponse = new PageResponse<>(
                List.of(item),
                0,
                1,
                1L,
                10,
                true
        );
        given(paymentService.getAllMyPayments(memberId, PaymentStatus.PAID, pageable)).willReturn(pageResponse);

        PageResponse<PaymentListResponse> result = paymentFacade.getAllMyPayments(memberId, PaymentStatus.PAID, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().get(0).status()).isEqualTo(PaymentStatus.PAID);
    }

    private void stubLocks() {
        lenient().when(paymentLockExecutor.executeWithOrderLock(any(UUID.class), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
        lenient().when(paymentLockExecutor.executeWithCourseLock(any(UUID.class), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
        lenient().when(paymentLockExecutor.executeWithPgKeyLock(anyString(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
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

    private Payment createPendingPayment(UUID orderId, UUID memberId, String pgKey) {
        return Payment.createPending(
                orderId,
                memberId,
                pgKey,
                100000L,
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

    private static class WebhookTransactionUnknown extends BaseWebhookTransaction {
        private WebhookTransactionUnknown(WebhookTransactionData data) {
            super(data);
        }
    }

    private static class WebhookTransactionCancelledData extends BaseWebhookTransaction {
        private WebhookTransactionCancelledData(WebhookTransactionData data) {
            super(data);
        }
    }

    private static class WebhookTransactionCancelledDataPartialCancelled extends BaseWebhookTransaction {
        private WebhookTransactionCancelledDataPartialCancelled(WebhookTransactionData data) {
            super(data);
        }
    }
}
