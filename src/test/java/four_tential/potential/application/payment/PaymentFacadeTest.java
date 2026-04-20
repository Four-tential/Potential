package four_tential.potential.application.payment;

import four_tential.potential.application.order.OrderService;
import four_tential.potential.application.order.WaitingListService;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.CommonExceptionEnum;
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
import four_tential.potential.infra.portone.PortOneWebhookHandler;
import four_tential.potential.presentation.payment.dto.PaymentCreateRequest;
import four_tential.potential.presentation.payment.dto.PaymentCreateResponse;
import four_tential.potential.presentation.payment.dto.PaymentDetailResponse;
import four_tential.potential.presentation.payment.dto.PaymentListResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
    private OrderService orderService;

    @Mock
    private WaitingListService waitingListService;

    @Mock
    private TransactionStatus transactionStatus;

    private Webhook savedWebhook;

    @BeforeEach
    void setUp() {
        savedWebhook = Webhook.createPendingRecord("test-webhook-id", "UNKNOWN", null);
        lenient().when(webhookService.findPendingPaidWebhook(anyString()))
                .thenReturn(Optional.empty());
        lenient().when(paymentService.findByOrderId(any(UUID.class)))
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
                PaymentPayWay.CARD
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
                "pg-key-1",
                100000L,
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
    @DisplayName("PortOne 결제가 실패 상태이면 payments 에 FAILED 결제를 저장한다")
    void createPayment_gatewayFailed_createsFailedPayment() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(
                orderId,
                "pg-key-failed",
                PaymentPayWay.CARD
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-failed",
                "FAILED",
                0L,
                "unknown"
        );
        Payment failedPayment = Payment.createPending(
                orderId,
                memberId,
                "pg-key-failed",
                100000L,
                100000L,
                PaymentPayWay.CARD
        );
        failedPayment.fail();

        given(paymentGateway.getPayment("pg-key-failed")).willReturn(gatewayResponse);
        stubOrderAndCourse(orderId, memberId, courseId, 1);
        given(paymentService.createFailedPayment(any(PaymentCreateCommand.class), eq("pg-key-failed"), eq(PaymentPayWay.CARD)))
                .willReturn(failedPayment);
        given(paymentService.getByPgKey("pg-key-failed")).willReturn(failedPayment);

        PaymentCreateResponse response = paymentFacade.createPayment(memberId, request);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentService).createFailedPayment(any(PaymentCreateCommand.class), eq("pg-key-failed"), eq(PaymentPayWay.CARD));
        verify(paymentService, never()).validateGatewayPayment(any(), anyString(), any(), any());
        verify(paymentService, never()).createPendingPayment(any(), anyString(), any());
        verify(paymentGateway, never()).cancelPayment(any());
    }

    @Test
    @DisplayName("이미 같은 주문과 같은 pgKey로 결제가 있으면 기존 결제를 반환하고 PortOne 취소를 요청하지 않는다")
    void createPayment_duplicateSamePgKey_returnsExistingPayment() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(
                orderId,
                "pg-key-duplicate",
                PaymentPayWay.CARD
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-duplicate",
                "PAID",
                100000L,
                "card"
        );
        Payment existingPayment = Payment.createPending(
                orderId,
                memberId,
                "pg-key-duplicate",
                100000L,
                100000L,
                PaymentPayWay.CARD
        );
        existingPayment.confirmPaid();

        given(paymentGateway.getPayment("pg-key-duplicate")).willReturn(gatewayResponse);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(createOrder(orderId, memberId, UUID.randomUUID(), 1)));
        given(paymentService.findByOrderId(orderId)).willReturn(Optional.of(existingPayment));
        given(paymentService.getByPgKey("pg-key-duplicate")).willReturn(existingPayment);

        PaymentCreateResponse response = paymentFacade.createPayment(memberId, request);

        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.orderId()).isEqualTo(orderId);
        verify(paymentGateway, never()).cancelPayment(any());
        verify(paymentService, never()).validateNoPayment(any(UUID.class));
        verify(paymentService, never()).createPendingPayment(any(), anyString(), any());
    }

    @Test
    @DisplayName("같은 주문에 다른 pgKey 결제가 이미 있으면 새 PortOne 결제를 취소한다")
    void createPayment_duplicateDifferentPgKey_cancelGatewayPayment() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(
                orderId,
                "pg-key-new",
                PaymentPayWay.CARD
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-new",
                "PAID",
                100000L,
                "card"
        );
        Payment existingPayment = Payment.createPending(
                orderId,
                memberId,
                "pg-key-old",
                100000L,
                100000L,
                PaymentPayWay.CARD
        );

        given(paymentGateway.getPayment("pg-key-new")).willReturn(gatewayResponse);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(createOrder(orderId, memberId, UUID.randomUUID(), 1)));
        given(paymentService.findByOrderId(orderId)).willReturn(Optional.of(existingPayment));
        given(paymentService.findByPgKey("pg-key-new")).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentFacade.createPayment(memberId, request))
                .isInstanceOf(ServiceErrorException.class);

        verify(paymentGateway).cancelPayment(PaymentGatewayRequest.of(
                "pg-key-new",
                100000L,
                "PAYMENT_CREATE_REJECTED"
        ));
        verify(paymentService, never()).createPendingPayment(any(), anyString(), any());
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
                PaymentPayWay.CARD
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
                "pg-key-first-webhook",
                100000L,
                100000L,
                PaymentPayWay.CARD
        );
        Payment paymentForWebhook = Payment.createPending(
                orderId,
                memberId,
                "pg-key-first-webhook",
                100000L,
                100000L,
                PaymentPayWay.CARD
        );
        Payment latestPayment = Payment.createPending(
                orderId,
                memberId,
                "pg-key-first-webhook",
                100000L,
                100000L,
                PaymentPayWay.CARD
        );
        latestPayment.confirmPaid();
        Webhook deferredWebhook = Webhook.createPendingRecord("webhook-first", "WebhookTransactionPaid", null);
        deferredWebhook.updatePgKey("pg-key-first-webhook");

        given(paymentGateway.getPayment("pg-key-first-webhook")).willReturn(gatewayResponse);
        stubOrderAndCourse(orderId, memberId, courseId, 1);
        given(paymentService.createPendingPayment(any(PaymentCreateCommand.class), eq("pg-key-first-webhook"), eq(PaymentPayWay.CARD)))
                .willReturn(createdPayment);
        given(webhookService.findPendingPaidWebhook("pg-key-first-webhook"))
                .willReturn(Optional.of(deferredWebhook));
        given(webhookService.prepareRetry(deferredWebhook, "WebhookTransactionPaid"))
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
        verify(orderService).completePayment(orderId);
        verify(waitingListService).completeOccupyingSeat(courseId, memberId);
        verify(webhookService).completeWebhook(deferredWebhook);
        verify(webhookService, never()).failWebhook(eq(deferredWebhook), any(), any());
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
                PaymentPayWay.CARD
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
    @DisplayName("결제 생성 시 주문 회원이 다르면 PortOne 결제를 취소하고 보류 웹훅 실패 기록을 요청한다")
    void createPayment_memberMismatch_cancelGatewayPayment() {
        UUID requestMemberId = UUID.randomUUID();
        UUID orderMemberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(
                orderId,
                "pg-key-member-mismatch",
                PaymentPayWay.CARD
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-member-mismatch",
                "PAID",
                100000L,
                "card"
        );
        Order order = createOrder(orderId, orderMemberId, courseId, 1);

        given(paymentGateway.getPayment("pg-key-member-mismatch")).willReturn(gatewayResponse);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentFacade.createPayment(requestMemberId, request))
                .isInstanceOf(ServiceErrorException.class);

        verify(paymentGateway).cancelPayment(PaymentGatewayRequest.of(
                "pg-key-member-mismatch",
                100000L,
                "PAYMENT_CREATE_REJECTED"
        ));
        verify(webhookService).failDeferredPaidWebhook(
                eq("pg-key-member-mismatch"),
                eq("PAYMENT_CREATE_REJECTED"),
                anyString()
        );
        verify(paymentService, never()).createPendingPayment(any(), anyString(), any());
    }

    @Test
    @DisplayName("이미 저장된 pgKey 결제라면 검증 실패가 발생해도 기존 PortOne 결제를 취소하지 않는다")
    void createPayment_existingPgKeyValidationFail_doesNotCancelGatewayPayment() {
        UUID requestMemberId = UUID.randomUUID();
        UUID orderMemberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(
                orderId,
                "pg-key-existing",
                PaymentPayWay.CARD
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-existing",
                "PAID",
                100000L,
                "card"
        );
        Order order = createOrder(orderId, orderMemberId, courseId, 1);
        Payment existingPayment = Payment.createPending(
                orderId,
                orderMemberId,
                "pg-key-existing",
                100000L,
                100000L,
                PaymentPayWay.CARD
        );

        given(paymentGateway.getPayment("pg-key-existing")).willReturn(gatewayResponse);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(paymentService.findByPgKey("pg-key-existing")).willReturn(Optional.of(existingPayment));

        assertThatThrownBy(() -> paymentFacade.createPayment(requestMemberId, request))
                .isInstanceOf(ServiceErrorException.class);

        verify(paymentGateway, never()).cancelPayment(any());
        verify(webhookService, never()).failDeferredPaidWebhook(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("결제 생성 시 주문 상태가 PENDING이 아니면 결제를 취소하고 보류 웹훅 실패 기록을 요청한다")
    void createPayment_orderStatusInvalid_cancelGatewayPayment() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(
                orderId,
                "pg-key-order-invalid",
                PaymentPayWay.CARD
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-order-invalid",
                "PAID",
                100000L,
                "card"
        );
        Order order = createOrder(orderId, memberId, courseId, 1);
        ReflectionTestUtils.setField(order, "status", OrderStatus.CANCELLED);
        Course course = createCourse(courseId);

        given(paymentGateway.getPayment("pg-key-order-invalid")).willReturn(gatewayResponse);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() -> paymentFacade.createPayment(memberId, request))
                .isInstanceOf(ServiceErrorException.class);

        verify(paymentGateway).cancelPayment(PaymentGatewayRequest.of(
                "pg-key-order-invalid",
                100000L,
                "PAYMENT_CREATE_REJECTED"
        ));
        verify(webhookService).failDeferredPaidWebhook(
                eq("pg-key-order-invalid"),
                eq("PAYMENT_CREATE_REJECTED"),
                anyString()
        );
    }

    @Test
    @DisplayName("결제 생성 시 주문 결제 기한이 지났으면 결제를 취소하고 FAILED 결제를 저장한다")
    void createPayment_expiredOrder_cancelGatewayPayment() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(
                orderId,
                "pg-key-expired-order",
                PaymentPayWay.CARD
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-expired-order",
                "PAID",
                100000L,
                "card"
        );
        Order order = createOrder(orderId, memberId, courseId, 1);
        ReflectionTestUtils.setField(order, "expireAt", LocalDateTime.now().minusMinutes(1));
        Course course = createCourse(courseId);

        given(paymentGateway.getPayment("pg-key-expired-order")).willReturn(gatewayResponse);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() -> paymentFacade.createPayment(memberId, request))
                .isInstanceOf(ServiceErrorException.class);

        verify(paymentGateway).cancelPayment(PaymentGatewayRequest.of(
                "pg-key-expired-order",
                100000L,
                "PAYMENT_CREATE_REJECTED"
        ));
        verify(paymentService).createFailedPayment(
                any(PaymentCreateCommand.class),
                eq("pg-key-expired-order"),
                eq(PaymentPayWay.CARD)
        );
    }

    @Test
    @DisplayName("결제 생성 시 코스 빈자리가 없으면 결제를 취소한다")
    void createPayment_noAvailableSeats_cancelGatewayPayment() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(
                orderId,
                "pg-key-no-seats",
                PaymentPayWay.CARD
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-no-seats",
                "PAID",
                100000L,
                "card"
        );
        Order order = createOrder(orderId, memberId, courseId, 1);
        Course course = createCourse(courseId);
        ReflectionTestUtils.setField(course, "capacity", 1);
        ReflectionTestUtils.setField(course, "confirmCount", 1);

        given(paymentGateway.getPayment("pg-key-no-seats")).willReturn(gatewayResponse);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() -> paymentFacade.createPayment(memberId, request))
                .isInstanceOf(ServiceErrorException.class);

        verify(paymentGateway).cancelPayment(PaymentGatewayRequest.of(
                "pg-key-no-seats",
                100000L,
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
                PaymentPayWay.CARD
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
        given(webhookService.isFinished("test-webhook-id")).willReturn(true);

        paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature");

        verify(webhookService).isFinished("test-webhook-id");
        verify(webhookService, never()).saveIncomingWebhook(any(), any(), any());
        verify(portOneWebhookHandler, never()).verify(any(), any(), any(), any());
        verify(transactionTemplate, never()).execute(any(TransactionCallback.class));
        verify(webhookService, never()).completeWebhook(any());
        verify(webhookService, never()).failWebhook(any(), any(), any());
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("수신 저장 직후 이미 완료 상태로 확인된 웹훅이면 이후 로직을 수행하지 않는다")
    void handleWebhook_completedAfterReceive_ignored() throws Exception {
        Webhook completedWebhook = Webhook.createPendingRecord("test-webhook-id", "UNKNOWN", null);
        completedWebhook.markCompleted();
        given(webhookService.isFinished("test-webhook-id")).willReturn(false);
        given(webhookService.saveIncomingWebhook("test-webhook-id", "UNKNOWN", "{}")).willReturn(completedWebhook);

        paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature");

        verify(webhookService).saveIncomingWebhook("test-webhook-id", "UNKNOWN", "{}");
        verify(portOneWebhookHandler, never()).verify(any(), any(), any(), any());
        verify(webhookService, never()).completeWebhook(any());
        verify(webhookService, never()).failWebhook(any(), any(), any());
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("서명 검증 실패 시 WebhookVerificationException 을 던지고 웹훅 실패 상태를 저장한다")
    void handleWebhook_verificationFail() throws Exception {
        given(webhookService.isFinished("test-webhook-id")).willReturn(false);
        stubReceiveTransaction();
        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willThrow(new WebhookVerificationException("verify failed", new RuntimeException()));

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(webhookService).saveIncomingWebhook("test-webhook-id", "UNKNOWN", "{}");
        verify(webhookService, never()).completeWebhook(any());
        verify(webhookService).failWebhook(eq(savedWebhook), eq("WEBHOOK_SIGNATURE_INVALID"), anyString());
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("이미 PAID 상태인 결제 완료 웹훅이면 멱등 처리 후 webhook 완료 상태로 저장한다")
    void handleWebhook_paid_alreadyPaid() throws Exception {
        UUID courseId = UUID.randomUUID();
        Payment payment = createPayment("payment-already-paid");
        payment.confirmPaid();
        Order order = createOrder(payment.getOrderId(), payment.getMemberId(), courseId, 1);

        stubSuccessTransactionFlow();
        given(paymentService.findByPgKey("payment-already-paid")).willReturn(Optional.of(payment));
        given(paymentService.getByPgKeyForUpdate("payment-already-paid")).willReturn(payment);
        given(orderRepository.findById(payment.getOrderId())).willReturn(Optional.of(order));
        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-already-paid"));
        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(paymentService, never()).confirmPaid(payment);
        verify(paymentService, never()).fail(any(Payment.class));
        verify(paymentGateway, never()).cancelPayment(any());
        verify(webhookService).completeWebhook(savedWebhook);
    }

    @Test
    @DisplayName("PENDING 이 아닌 결제에 Paid 웹훅이 오면 PortOne 결제 취소를 요청한다")
    void handleWebhook_paid_invalidPaymentStatus_cancelGatewayPayment() throws Exception {
        UUID courseId = UUID.randomUUID();
        Payment payment = createPayment("payment-invalid-status");
        payment.fail();
        Order order = createOrder(payment.getOrderId(), payment.getMemberId(), courseId, 1);

        stubSuccessTransactionFlow();
        given(paymentService.findByPgKey("payment-invalid-status")).willReturn(Optional.of(payment));
        given(paymentService.getByPgKeyForUpdate("payment-invalid-status")).willReturn(payment);
        given(orderRepository.findById(payment.getOrderId())).willReturn(Optional.of(order));
        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-invalid-status"));
        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(paymentGateway).cancelPayment(PaymentGatewayRequest.of(
                "payment-invalid-status",
                100000L,
                "PAYMENT_STATUS_INVALID"
        ));
        verify(webhookService).failWebhook(eq(savedWebhook), eq("PAYMENT_STATUS_INVALID"), anyString());
        verify(webhookService, never()).completeWebhook(savedWebhook);
    }

    @Test
    @DisplayName("결제 회원과 주문 회원이 다르면 결제 실패 처리 후 PortOne 결제 취소를 요청한다")
    void handleWebhook_paid_memberMismatch_cancelGatewayPayment() throws Exception {
        UUID courseId = UUID.randomUUID();
        Payment payment = createPayment("payment-member-mismatch");
        Order order = createOrder(payment.getOrderId(), UUID.randomUUID(), courseId, 1);
        Course course = createCourse(courseId);

        stubSuccessTransactionFlow();
        given(paymentService.findByPgKey("payment-member-mismatch")).willReturn(Optional.of(payment));
        given(paymentService.getByPgKeyForUpdate("payment-member-mismatch")).willReturn(payment);
        given(orderRepository.findById(payment.getOrderId())).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-member-mismatch"));
        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(paymentService).fail(payment);
        verify(paymentGateway).cancelPayment(PaymentGatewayRequest.of(
                "payment-member-mismatch",
                100000L,
                "PAYMENT_ORDER_MEMBER_MISMATCH"
        ));
        verify(webhookService).failWebhook(eq(savedWebhook), eq("PAYMENT_ORDER_MEMBER_MISMATCH"), anyString());
        verify(webhookService, never()).completeWebhook(savedWebhook);
    }

    @Test
    @DisplayName("주문 상태가 PENDING 이 아니면 결제 실패 처리 후 PortOne 결제 취소를 요청한다")
    void handleWebhook_paid_orderStatusInvalid_cancelGatewayPayment() throws Exception {
        UUID courseId = UUID.randomUUID();
        Payment payment = createPayment("payment-order-invalid");
        Order order = createOrder(payment.getOrderId(), payment.getMemberId(), courseId, 1);
        ReflectionTestUtils.setField(order, "status", OrderStatus.CANCELLED);
        Course course = createCourse(courseId);

        stubSuccessTransactionFlow();
        given(paymentService.findByPgKey("payment-order-invalid")).willReturn(Optional.of(payment));
        given(paymentService.getByPgKeyForUpdate("payment-order-invalid")).willReturn(payment);
        given(orderRepository.findById(payment.getOrderId())).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-order-invalid"));
        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(paymentService).fail(payment);
        verify(paymentGateway).cancelPayment(PaymentGatewayRequest.of(
                "payment-order-invalid",
                100000L,
                "ORDER_STATUS_INVALID"
        ));
        verify(webhookService).failWebhook(eq(savedWebhook), eq("ORDER_STATUS_INVALID"), anyString());
        verify(webhookService, never()).completeWebhook(savedWebhook);
    }

    @Test
    @DisplayName("결제 기한이 지난 주문에 Paid 웹훅이 오면 결제 실패 처리 후 PortOne 결제 취소를 요청한다")
    void handleWebhook_paid_orderExpired_cancelGatewayPayment() throws Exception {
        UUID courseId = UUID.randomUUID();
        Payment payment = createPayment("payment-expired-order");
        Order order = createOrder(payment.getOrderId(), payment.getMemberId(), courseId, 1);
        ReflectionTestUtils.setField(order, "expireAt", LocalDateTime.now().minusMinutes(1));
        Course course = createCourse(courseId);

        stubSuccessTransactionFlow();
        given(paymentService.findByPgKey("payment-expired-order")).willReturn(Optional.of(payment));
        given(paymentService.getByPgKeyForUpdate("payment-expired-order")).willReturn(payment);
        given(orderRepository.findById(payment.getOrderId())).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-expired-order"));
        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(paymentService).fail(payment);
        verify(paymentGateway).cancelPayment(PaymentGatewayRequest.of(
                "payment-expired-order",
                100000L,
                "PAYMENT_DEADLINE_EXCEEDED"
        ));
        verify(webhookService).failWebhook(eq(savedWebhook), eq("PAYMENT_DEADLINE_EXCEEDED"), anyString());
        verify(webhookService, never()).completeWebhook(savedWebhook);
    }

    @Test
    @DisplayName("코스 빈자리가 없는데 Paid 웹훅이 오면 결제 실패 처리 후 PortOne 결제 취소를 요청한다")
    void handleWebhook_paid_noAvailableSeats_cancelGatewayPayment() throws Exception {
        UUID courseId = UUID.randomUUID();
        Payment payment = createPayment("payment-no-seats");
        Order order = createOrder(payment.getOrderId(), payment.getMemberId(), courseId, 1);
        Course course = createCourse(courseId);
        ReflectionTestUtils.setField(course, "capacity", 1);
        ReflectionTestUtils.setField(course, "confirmCount", 1);

        stubSuccessTransactionFlow();
        given(paymentService.findByPgKey("payment-no-seats")).willReturn(Optional.of(payment));
        given(paymentService.getByPgKeyForUpdate("payment-no-seats")).willReturn(payment);
        given(orderRepository.findById(payment.getOrderId())).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-no-seats"));
        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(paymentService).fail(payment);
        verify(paymentGateway).cancelPayment(PaymentGatewayRequest.of(
                "payment-no-seats",
                100000L,
                "NO_AVAILABLE_SEATS"
        ));
        verify(webhookService).failWebhook(eq(savedWebhook), eq("NO_AVAILABLE_SEATS"), anyString());
        verify(webhookService, never()).completeWebhook(savedWebhook);
    }

    @Test
    @DisplayName("결제 취소 요청 실패 시 webhook 실패 상태 저장 후 예외를 다시 던진다")
    void handleWebhook_paid_cancelGatewayFail_marksWebhookFailed() throws Exception {
        UUID courseId = UUID.randomUUID();
        Payment payment = createPayment("payment-cancel-fail");
        payment.fail();
        Order order = createOrder(payment.getOrderId(), payment.getMemberId(), courseId, 1);

        stubSuccessTransactionFlow();
        given(paymentService.findByPgKey("payment-cancel-fail")).willReturn(Optional.of(payment));
        given(paymentService.getByPgKeyForUpdate("payment-cancel-fail")).willReturn(payment);
        given(orderRepository.findById(payment.getOrderId())).willReturn(Optional.of(order));
        doThrow(new RuntimeException("cancel failed"))
                .when(paymentGateway)
                .cancelPayment(any(PaymentGatewayRequest.class));
        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-cancel-fail"));
        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(webhookService).failWebhook(eq(savedWebhook), eq("PORTONE_CANCEL_FAILED"), anyString());
        verify(webhookService, never()).completeWebhook(savedWebhook);
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
                "payment-1",
                100000L,
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
        verify(orderService).completePayment(orderId);
        verify(waitingListService).completeOccupyingSeat(courseId, memberId);
        verify(paymentService, never()).fail(any(Payment.class));
        verify(webhookService).completeWebhook(savedWebhook);
        verify(webhookService, never()).failWebhook(eq(savedWebhook), any(), any());
        assertThat(course.getConfirmCount()).isEqualTo(1);

        Object eventStatus = ReflectionTestUtils.getField(savedWebhook, "eventStatus");
        if (eventStatus != null) {
            assertThat(eventStatus).isEqualTo("WebhookTransactionPaid");
        }
    }

    @Test
    @DisplayName("결제 완료 웹훅이 먼저 왔지만 결제가 아직 저장되지 않았으면 실패가 아니라 대기 상태로 남긴다")
    void handleWebhook_paid_beforePaymentSaved_defer() throws Exception {
        given(webhookService.isFinished("test-webhook-id")).willReturn(false);
        given(webhookService.saveIncomingWebhook("test-webhook-id", "UNKNOWN", "{}")).willReturn(savedWebhook);
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
        verify(webhookService).deferPaidWebhook(savedWebhook, "WebhookTransactionPaid", "payment-not-yet-saved");
        verify(paymentService, never()).confirmPaid(any());
        verify(webhookService, never()).completeWebhook(savedWebhook);
        verify(webhookService, never()).failWebhook(eq(savedWebhook), any(), any());
    }

    @Test
    @DisplayName("결제 실패 웹훅이면 결제 실패 처리 후 webhook 완료 상태로 저장한다")
    void handleWebhook_failed() throws Exception {
        Payment payment = createPayment("payment-2");
        stubSuccessTransactionFlow();
        given(paymentService.findByPgKeyForUpdate("payment-2")).willReturn(Optional.of(payment));
        WebhookTransactionFailed verified =
                new WebhookTransactionFailed(new TestWebhookTransactionData("payment-2"));

        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(paymentService).fail(payment);
        verify(paymentService, never()).confirmPaid(any());
        verify(webhookService).completeWebhook(savedWebhook);
        verify(webhookService, never()).failWebhook(eq(savedWebhook), any(), any());
    }

    @Test
    @DisplayName("결제 취소 웹훅이면 결제 실패 처리 후 webhook 완료 상태로 저장한다")
    void handleWebhook_cancelled() throws Exception {
        Payment payment = createPayment("payment-3");
        stubSuccessTransactionFlow();
        given(paymentService.findByPgKeyForUpdate("payment-3")).willReturn(Optional.of(payment));
        WebhookTransactionCancelled verified =
                new WebhookTransactionCancelled(new TestWebhookTransactionData("payment-3"));

        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(paymentService).fail(payment);
        verify(paymentService, never()).confirmPaid(any());
        verify(webhookService).completeWebhook(savedWebhook);
        verify(webhookService, never()).failWebhook(eq(savedWebhook), any(), any());
    }

    @Test
    @DisplayName("결제 실패 웹훅이 먼저 도착해 결제가 없으면 예외 없이 webhook 만 완료 처리한다")
    void handleWebhook_failedWithoutPayment_completesWebhookOnly() throws Exception {
        stubSuccessTransactionFlow();
        given(paymentService.findByPgKeyForUpdate("payment-missing")).willReturn(Optional.empty());
        WebhookTransactionFailed verified =
                new WebhookTransactionFailed(new TestWebhookTransactionData("payment-missing"));

        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(paymentService, never()).fail(any(Payment.class));
        verify(paymentService, never()).confirmPaid(any());
        verify(webhookService).completeWebhook(savedWebhook);
        verify(webhookService, never()).failWebhook(eq(savedWebhook), any(), any());
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
        verify(webhookService).completeWebhook(savedWebhook);
        verify(webhookService, never()).failWebhook(eq(savedWebhook), any(), any());
    }

    @Test
    @DisplayName("트랜잭션 타입이 아닌 웹훅이면 결제 서비스 호출 없이 webhook 완료 상태만 저장한다")
    void handleWebhook_nonTransactionWebhook() throws Exception {
        given(webhookService.isFinished("test-webhook-id")).willReturn(false);
        given(webhookService.saveIncomingWebhook("test-webhook-id", "UNKNOWN", "{}")).willReturn(savedWebhook);
        PlainWebhook verified = new PlainWebhook();

        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(webhookService).updateEventStatus(savedWebhook, "PlainWebhook");
        verify(paymentService, never()).confirmPaid(any());
        verify(paymentService, never()).fail(any(Payment.class));
        verify(webhookService).completeWebhook(savedWebhook);
        verify(webhookService, never()).failWebhook(eq(savedWebhook), any(), any());
    }

    @Test
    @DisplayName("비즈니스 예외가 발생하면 웹훅을 실패 기록하고 200 응답 흐름으로 종료한다")
    void handleWebhook_businessServiceError_recordsFailedWebhook() throws Exception {
        given(webhookService.isFinished("test-webhook-id")).willReturn(false);
        given(webhookService.saveIncomingWebhook("test-webhook-id", "UNKNOWN", "{}")).willReturn(savedWebhook);
        given(transactionTemplate.execute(any(TransactionCallback.class)))
                .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_AMOUNT_MISMATCH));
        WebhookTransactionFailed verified =
                new WebhookTransactionFailed(new TestWebhookTransactionData("payment-service-error"));

        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(webhookService).failWebhook(eq(savedWebhook), eq("WEBHOOK_BUSINESS_FAILED"), anyString());
        verify(webhookService, never()).completeWebhook(savedWebhook);
    }

    @Test
    @DisplayName("비즈니스 처리 중 예외가 발생하면 webhook 실패 상태 저장 후 예외를 다시 던진다")
    void handleWebhook_businessFail() throws Exception {
        given(webhookService.isFinished("test-webhook-id")).willReturn(false);

        given(transactionTemplate.execute(any(TransactionCallback.class)))
                .willThrow(new RuntimeException("business failed"));

        given(webhookService.saveIncomingWebhook("test-webhook-id", "UNKNOWN", "{}")).willReturn(savedWebhook);

        WebhookTransactionFailed verified =
                new WebhookTransactionFailed(new TestWebhookTransactionData("payment-5"));

        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatThrownBy(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("business failed");

        verify(webhookService).failWebhook(eq(savedWebhook), eq("WEBHOOK_UNEXPECTED_ERROR"), anyString());
        verify(webhookService, never()).completeWebhook(savedWebhook);
    }

    @Test
    @DisplayName("실패 결제라도 요청 결제 수단이 카드가 아니면 FAILED 결제를 저장하지 않는다")
    void createPayment_failedGatewayUnsupportedPayWay_throws() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(
                orderId,
                "pg-key-failed-easy-pay",
                PaymentPayWay.EASY_PAY
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-failed-easy-pay",
                "FAILED",
                0L,
                "easyPay"
        );

        given(paymentGateway.getPayment("pg-key-failed-easy-pay")).willReturn(gatewayResponse);
        stubOrderAndCourse(orderId, memberId, courseId, 1);

        assertThatThrownBy(() -> paymentFacade.createPayment(memberId, request))
                .isInstanceOf(ServiceErrorException.class);

        verify(paymentService, never()).createFailedPayment(any(), anyString(), any());
        verify(paymentGateway, never()).cancelPayment(any());
    }

    @Test
    @DisplayName("실패 결제 조회 결과의 pgKey가 요청 pgKey와 다르면 FAILED 결제를 저장하지 않는다")
    void createPayment_failedGatewayPgKeyMismatch_throws() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(
                orderId,
                "pg-key-request",
                PaymentPayWay.CARD
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-other",
                "FAILED",
                0L,
                "card"
        );

        given(paymentGateway.getPayment("pg-key-request")).willReturn(gatewayResponse);
        stubOrderAndCourse(orderId, memberId, courseId, 1);

        assertThatThrownBy(() -> paymentFacade.createPayment(memberId, request))
                .isInstanceOf(ServiceErrorException.class);

        verify(paymentService, never()).createFailedPayment(any(), anyString(), any());
        verify(paymentGateway, never()).cancelPayment(any());
    }

    @Test
    @DisplayName("거절된 결제의 FAILED 기록 저장이 실패해도 원래 결제 거절 흐름은 유지한다")
    void createPayment_rejectedPaymentRecordFail_doesNotMaskOriginalException() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(
                orderId,
                "pg-key-record-fail",
                PaymentPayWay.CARD
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-record-fail",
                "PAID",
                100000L,
                "card"
        );
        Order order = createOrder(orderId, memberId, courseId, 1);
        ReflectionTestUtils.setField(order, "expireAt", LocalDateTime.now().minusMinutes(1));
        Course course = createCourse(courseId);

        given(paymentGateway.getPayment("pg-key-record-fail")).willReturn(gatewayResponse);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(paymentService.findByPgKey("pg-key-record-fail")).willReturn(Optional.empty());
        doThrow(new RuntimeException("record failed"))
                .when(paymentService)
                .createFailedPayment(any(PaymentCreateCommand.class), eq("pg-key-record-fail"), eq(PaymentPayWay.CARD));

        assertThatThrownBy(() -> paymentFacade.createPayment(memberId, request))
                .isInstanceOf(ServiceErrorException.class);

        verify(paymentGateway).cancelPayment(PaymentGatewayRequest.of(
                "pg-key-record-fail",
                100000L,
                "PAYMENT_CREATE_REJECTED"
        ));
        verify(webhookService).failDeferredPaidWebhook(
                eq("pg-key-record-fail"),
                eq("PAYMENT_CREATE_REJECTED"),
                anyString()
        );
    }

    @Test
    @DisplayName("거절된 결제의 PortOne 취소가 실패해도 원래 결제 생성 예외를 유지한다")
    void createPayment_rejectedPaymentCancelFail_doesNotMaskOriginalException() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(
                orderId,
                "pg-key-cancel-fail-safe",
                PaymentPayWay.CARD
        );
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse(
                "pg-key-cancel-fail-safe",
                "PAID",
                100000L,
                "card"
        );
        Order order = createOrder(orderId, memberId, courseId, 1);
        ReflectionTestUtils.setField(order, "expireAt", LocalDateTime.now().minusMinutes(1));
        Course course = createCourse(courseId);

        given(paymentGateway.getPayment("pg-key-cancel-fail-safe")).willReturn(gatewayResponse);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(paymentService.findByPgKey("pg-key-cancel-fail-safe")).willReturn(Optional.empty());
        doThrow(new RuntimeException("cancel failed"))
                .when(paymentGateway)
                .cancelPayment(any(PaymentGatewayRequest.class));

        assertThatThrownBy(() -> paymentFacade.createPayment(memberId, request))
                .isInstanceOf(ServiceErrorException.class);

        verify(paymentGateway).cancelPayment(any(PaymentGatewayRequest.class));
        verify(webhookService).failDeferredPaidWebhook(
                eq("pg-key-cancel-fail-safe"),
                eq("PAYMENT_CREATE_REJECTED"),
                anyString()
        );
    }

    @Test
    @DisplayName("결제 완료 후 Redis 선점 정리가 실패해도 웹훅은 완료 처리한다")
    void handleWebhook_paid_occupancyCleanupFail_stillCompletesWebhook() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Order order = createOrder(orderId, memberId, courseId, 1);
        Course course = createCourse(courseId);
        Payment payment = Payment.createPending(
                orderId,
                memberId,
                "payment-occupancy-cleanup-fail",
                100000L,
                100000L,
                PaymentPayWay.CARD
        );

        stubSuccessTransactionFlow();
        given(paymentService.findByPgKey("payment-occupancy-cleanup-fail")).willReturn(Optional.of(payment));
        given(paymentService.getByPgKeyForUpdate("payment-occupancy-cleanup-fail")).willReturn(payment);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        doThrow(new RuntimeException("redis failed"))
                .when(waitingListService)
                .completeOccupyingSeat(courseId, memberId);

        WebhookTransactionPaid verified =
                new WebhookTransactionPaid(new TestWebhookTransactionData("payment-occupancy-cleanup-fail"));
        given(portOneWebhookHandler.verify("{}", "test-webhook-id", "signature", "timestamp"))
                .willReturn(verified);

        assertThatCode(() ->
                paymentFacade.handleWebhook("{}", "test-webhook-id", "timestamp", "signature"))
                .doesNotThrowAnyException();

        verify(paymentService).confirmPaid(payment);
        verify(orderService).completePayment(orderId);
        verify(waitingListService).completeOccupyingSeat(courseId, memberId);
        verify(webhookService).completeWebhook(savedWebhook);
        verify(webhookService, never()).failWebhook(eq(savedWebhook), any(), any());
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
        given(webhookService.saveIncomingWebhook("test-webhook-id", "UNKNOWN", "{}")).willReturn(savedWebhook);
    }

    private void stubSuccessTransactionFlow() {
        given(webhookService.isFinished("test-webhook-id")).willReturn(false);

        given(transactionTemplate.execute(any(TransactionCallback.class)))
                .willAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(transactionStatus);
                });

        given(webhookService.saveIncomingWebhook("test-webhook-id", "UNKNOWN", "{}")).willReturn(savedWebhook);
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

    @Test
    @DisplayName("본인 결제가 있으면 PaymentDetailResponse 를 반환한다")
    void getMyPayment_returns_detail_response() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();
        PaymentDetailResponse expected = new PaymentDetailResponse(
                paymentId, orderId, "소도구 필라테스 입문반", 5,
                125000L, 125000L,
                PaymentPayWay.CARD, PaymentStatus.PAID,
                LocalDateTime.of(2025, 1, 1, 10, 0)
        );
        given(paymentService.getMyPayment(paymentId, memberId))
                .willReturn(expected);

        PaymentDetailResponse result = paymentFacade.getMyPayment(memberId, paymentId);

        assertThat(result).isEqualTo(expected);
        verify(paymentService).getMyPayment(paymentId, memberId);
    }

    @Test
    @DisplayName("결제가 없거나 타인의 결제면 ServiceErrorException 이 전파된다")
    void getMyPayment_propagates_exception_when_not_found() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        given(paymentService.getMyPayment(paymentId, memberId))
                .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT));

        assertThatThrownBy(() -> paymentFacade.getMyPayment(memberId, paymentId))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("status 가 null 이면 전체 결제 목록 PageResponse 를 반환한다")
    void getMyPayments_returns_all_when_status_null() {
        UUID memberId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        PaymentListResponse item = new PaymentListResponse(
                UUID.randomUUID(), UUID.randomUUID(), "소도구 필라테스 입문반", 5,
                125000L, PaymentStatus.PAID, LocalDateTime.of(2025, 1, 1, 10, 0)
        );
        Page<PaymentListResponse> page = new PageImpl<>(List.of(item), pageable, 1);
        given(paymentService.getAllMyPayments(memberId, null, pageable))
                .willReturn(page);

        var result = paymentFacade.getAllMyPayments(memberId, null, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.currentPage()).isZero();
        verify(paymentService).getAllMyPayments(memberId, null, pageable);
    }

    @Test
    @DisplayName("status 가 PAID 이면 PAID 결제 목록 PageResponse 를 반환한다")
    void getMyPayments_returns_filtered_when_status_paid() {
        UUID memberId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        PaymentListResponse item = new PaymentListResponse(
                UUID.randomUUID(), UUID.randomUUID(), "소도구 필라테스 입문반", 5,
                125000L, PaymentStatus.PAID, LocalDateTime.of(2025, 1, 1, 10, 0)
        );
        Page<PaymentListResponse> page = new PageImpl<>(List.of(item), pageable, 1);
        given(paymentService.getAllMyPayments(memberId, PaymentStatus.PAID, pageable))
                .willReturn(page);

        var result = paymentFacade.getAllMyPayments(memberId, PaymentStatus.PAID, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).status()).isEqualTo(PaymentStatus.PAID);
        verify(paymentService).getAllMyPayments(memberId, PaymentStatus.PAID, pageable);
    }

    @Test
    @DisplayName("결제 내역이 없으면 빈 PageResponse 를 반환한다")
    void getMyPayments_returns_empty_page() {
        UUID memberId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Page<PaymentListResponse> emptyPage = new PageImpl<>(List.of(), pageable, 0);
        given(paymentService.getAllMyPayments(memberId, null, pageable))
                .willReturn(emptyPage);

        var result = paymentFacade.getAllMyPayments(memberId, null, pageable);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.isLast()).isTrue();
    }

    @Test
    @DisplayName("페이지네이션 메타 정보가 올바르게 반환된다")
    void getMyPayments_returns_correct_pagination_meta() {
        UUID memberId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(1, 10);
        List<PaymentListResponse> items = List.of(
                new PaymentListResponse(UUID.randomUUID(), UUID.randomUUID(), "강좌A", 1,
                        50000L, PaymentStatus.PAID, LocalDateTime.now()),
                new PaymentListResponse(UUID.randomUUID(), UUID.randomUUID(), "강좌B", 2,
                        100000L, PaymentStatus.PENDING, LocalDateTime.now())
        );
        Page<PaymentListResponse> page = new PageImpl<>(items, pageable, 12);
        given(paymentService.getAllMyPayments(memberId, null, pageable))
                .willReturn(page);

        var result = paymentFacade.getAllMyPayments(memberId, null, pageable);

        assertThat(result.currentPage()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(12);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.isLast()).isTrue();
    }

    @Test
    @DisplayName("전체 취소 완료 웹훅(CancelledCancelled) 수신 시 별도 처리 없이 COMPLETED 로 종료한다")
    void handleWebhook_cancelledCancelled_completes_without_payment_change() throws Exception {
        String pgKey = "pg-cancelled-completed";

        given(webhookService.isFinished("webhook-cc")).willReturn(false);
        Webhook webhook = Webhook.createPendingRecord("webhook-cc", "UNKNOWN", "{}");
        given(webhookService.saveIncomingWebhook("webhook-cc", "UNKNOWN", "{}"))
                .willReturn(webhook);
        given(portOneWebhookHandler.verify("{}", "webhook-cc", "signature", "timestamp"))
                .willReturn(new WebhookTransactionCancelledCancelled(new TestWebhookTransactionData(pgKey)));
        given(transactionTemplate.execute(any(TransactionCallback.class)))
                .willAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(transactionStatus);
                });
        given(webhookService.merge(any())).willReturn(webhook);

        paymentFacade.handleWebhook("{}", "webhook-cc", "timestamp", "signature");

        verify(paymentService, never()).fail(any());
        verify(paymentService, never()).confirmPaid(any());
        verify(webhookService).completeWebhook(webhook);
    }

    @Test
    @DisplayName("부분 취소 완료 웹훅(CancelledPartialCancelled) 수신 시 별도 처리 없이 COMPLETED 로 종료한다")
    void handleWebhook_cancelledPartialCancelled_completes_without_payment_change() throws Exception {
        String pgKey = "pg-partial-cancelled";

        given(webhookService.isFinished("webhook-pc")).willReturn(false);
        Webhook webhook = Webhook.createPendingRecord("webhook-pc", "UNKNOWN", "{}");
        given(webhookService.saveIncomingWebhook("webhook-pc", "UNKNOWN", "{}"))
                .willReturn(webhook);
        given(portOneWebhookHandler.verify("{}", "webhook-pc", "signature", "timestamp"))
                .willReturn(new WebhookTransactionCancelledPartialCancelled(new TestWebhookTransactionData(pgKey)));
        given(transactionTemplate.execute(any(TransactionCallback.class)))
                .willAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(transactionStatus);
                });
        given(webhookService.merge(any())).willReturn(webhook);

        paymentFacade.handleWebhook("{}", "webhook-pc", "timestamp", "signature");

        verify(paymentService, never()).fail(any());
        verify(paymentService, never()).confirmPaid(any());
        verify(webhookService).completeWebhook(webhook);
    }

    // ─────────────────────────────────────────────
    // 내부 클래스 (기존 파일에 추가)
    // ─────────────────────────────────────────────

    private static class WebhookTransactionCancelledCancelled extends BaseWebhookTransaction {
        private WebhookTransactionCancelledCancelled(WebhookTransactionData data) {
            super(data);
        }
    }

    private static class WebhookTransactionCancelledPartialCancelled extends BaseWebhookTransaction {
        private WebhookTransactionCancelledPartialCancelled(WebhookTransactionData data) {
            super(data);
        }
    }
}
