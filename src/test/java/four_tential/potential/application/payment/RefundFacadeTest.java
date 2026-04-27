package four_tential.potential.application.payment;

import four_tential.potential.application.order.OrderService;
import four_tential.potential.application.order.WaitingListService;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.fixture.CourseFixture;
import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.entity.Refund;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.domain.payment.enums.RefundReason;
import four_tential.potential.domain.payment.enums.RefundStatus;
import four_tential.potential.domain.payment.port.PaymentGateway;
import four_tential.potential.domain.payment.port.PaymentGatewayRequest;
import four_tential.potential.domain.payment.repository.RefundPreviewData;
import four_tential.potential.presentation.payment.dto.RefundCourseResponse;
import four_tential.potential.presentation.payment.dto.RefundDetailResponse;
import four_tential.potential.presentation.payment.dto.RefundListResponse;
import four_tential.potential.presentation.payment.dto.RefundPreviewResponse;
import four_tential.potential.presentation.payment.dto.RefundResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundFacadeTest {

    @InjectMocks
    private RefundFacade refundFacade;

    @Mock
    private RefundService refundService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private OrderService orderService;

    @Mock
    private WaitingListService waitingListService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private PaymentDistributedLockExecutor paymentLockExecutor;

    @Mock
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        lenient().when(paymentLockExecutor.executeWithPgKeyLock(any(String.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        lenient().when(paymentLockExecutor.executeWithCourseLock(any(UUID.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    @Test
    @DisplayName("getRefundPreview는 projection 데이터와 코스 시작 시간을 RefundService에 전달한다")
    void getRefundPreview_uses_projection_data_and_course_start_time() {
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        LocalDateTime startAt = LocalDateTime.now().plusDays(8);

        RefundPreviewData data = new RefundPreviewData(
                paymentId,
                memberId,
                125000L,
                PaymentStatus.PAID.name(),
                courseId,
                "Test Course",
                5,
                BigInteger.valueOf(25000L)
        );
        Course course = createCourse(courseId, startAt, 5);
        RefundPreviewResponse expected = RefundPreviewResponse.of(
                paymentId,
                "Test Course",
                startAt,
                5,
                25000L,
                125000L,
                true
        );

        given(paymentService.getRefundPreviewData(paymentId, memberId)).willReturn(data);
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(refundService.getRefundPreview(data, startAt)).willReturn(expected);

        RefundPreviewResponse result = refundFacade.getRefundPreview(memberId, paymentId);

        assertThat(result).isEqualTo(expected);
        verify(refundService).getRefundPreview(data, startAt);
    }

    @Test
    @DisplayName("getRefundPreview는 결제 projection이 없으면 예외가 발생한다")
    void getRefundPreview_throws_when_payment_not_found() {
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        given(paymentService.getRefundPreviewData(paymentId, memberId))
                .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT));

        assertThatThrownBy(() -> refundFacade.getRefundPreview(memberId, paymentId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT.getMessage());
    }

    @Test
    @DisplayName("refundPaidOrderByStudent는 남은 수강권이 0개가 되면 전액 환불로 처리한다")
    void refundByStudent_full_refund() {
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Payment payment = createPaidPayment(paymentId, orderId, memberId, 150000L);
        Order order = createPaidOrder(orderId, memberId, courseId, 3, 50000L);
        Course course = createCourse(courseId, LocalDateTime.now().plusDays(10), 3);
        Refund refund = createRefund(payment, 150000L, 3);

        stubSuccessfulRefund(payment, order, course, refund, 0L);
        doAnswer(invocation -> {
            invocation.getArgument(0, Payment.class).refund();
            return null;
        }).when(paymentService).refund(any(Payment.class));
        given(orderService.applyRefund(orderId, memberId, 3)).willAnswer(invocation -> {
            order.applyRefund(3, LocalDateTime.now());
            return order;
        });

        RefundResponse result = refundFacade.refundPaidOrderByStudent(memberId, orderId, 3);

        assertThat(result.refundPrice()).isEqualTo(150000L);
        assertThat(result.cancelCount()).isEqualTo(3);
        assertThat(result.remainingCount()).isZero();
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentService).refund(payment);
        verify(orderService).applyRefund(orderId, memberId, 3);
        verify(waitingListService).recoverCapacity(courseId, memberId, 3);

        PaymentGatewayRequest gatewayRequest = captureGatewayRequest();
        assertThat(gatewayRequest.pgKey()).isEqualTo(payment.getPgKey());
        assertThat(gatewayRequest.amount()).isEqualTo(150000L);
        assertThat(gatewayRequest.reason()).isEqualTo("CANCEL");
    }

    @Test
    @DisplayName("refundPaidOrderByStudent는 수강권이 남아 있으면 부분 환불로 처리한다")
    void refundByStudent_partial_refund() {
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Payment payment = createPaidPayment(paymentId, orderId, memberId, 75000L);
        Order order = createPaidOrder(orderId, memberId, courseId, 3, 25000L);
        Course course = createCourse(courseId, LocalDateTime.now().plusDays(10), 3);
        Refund refund = createRefund(payment, 25000L, 1);

        stubSuccessfulRefund(payment, order, course, refund, 0L);
        doAnswer(invocation -> {
            invocation.getArgument(0, Payment.class).partRefund();
            return null;
        }).when(paymentService).partRefund(any(Payment.class));
        given(orderService.applyRefund(orderId, memberId, 1)).willAnswer(invocation -> {
            order.applyRefund(1, LocalDateTime.now());
            return order;
        });

        RefundResponse result = refundFacade.refundPaidOrderByStudent(memberId, orderId, 1);

        assertThat(result.refundPrice()).isEqualTo(25000L);
        assertThat(result.cancelCount()).isEqualTo(1);
        assertThat(result.remainingCount()).isEqualTo(2);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.PART_REFUNDED);
        verify(paymentService).partRefund(payment);
        verify(waitingListService).recoverCapacity(courseId, memberId, 1);
        assertThat(captureGatewayRequest().amount()).isEqualTo(25000L);
    }

    @Test
    @DisplayName("refundPaidOrderByStudent는 게이트웨이 호출이 실패하면 FAILED 환불 이력을 남긴다")
    void refundByStudent_records_failed_refund_when_gateway_fails() {
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Payment payment = createPaidPayment(paymentId, orderId, memberId, 50000L);
        Order order = createPaidOrder(orderId, memberId, courseId, 1, 50000L);
        Course course = createCourse(courseId, LocalDateTime.now().plusDays(10), 1);

        stubSuccessfulRefund(payment, order, course, createRefund(payment, 50000L, 1), 0L);
        doThrow(new RuntimeException("gateway fail")).when(paymentGateway).cancelPayment(any());

        assertThatThrownBy(() -> refundFacade.refundPaidOrderByStudent(memberId, orderId, 1))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(PaymentExceptionEnum.ERR_REFUND_PROCESS_FAILED.getMessage());

        verify(refundService).createFailed(paymentId, 50000L, 1, RefundReason.CANCEL);
        verify(orderService, never()).applyRefund(any(), any(), anyInt());
        verify(waitingListService, never()).recoverCapacity(any(), any(), anyInt());
    }

    @Test
    @DisplayName("refundPaidOrderByStudent는 환불 가능 기간이 지나면 게이트웨이 호출 전에 중단한다")
    void refundByStudent_throws_when_refund_period_expired() {
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Payment payment = createPaidPayment(paymentId, orderId, memberId, 50000L);
        Order order = createPaidOrder(orderId, memberId, courseId, 1, 50000L);
        Course course = createCourse(courseId, LocalDateTime.now().plusDays(3), 1);

        given(paymentService.findByOrderId(orderId)).willReturn(Optional.of(payment));
        given(paymentService.getByPgKeyForUpdate(payment.getPgKey())).willReturn(payment);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(refundService.isRefundable(course.getStartAt())).willReturn(false);

        assertThatThrownBy(() -> refundFacade.refundPaidOrderByStudent(memberId, orderId, 1))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(PaymentExceptionEnum.ERR_REFUND_NOT_ALLOWED.getMessage());

        verify(paymentGateway, never()).cancelPayment(any());
    }

    @Test
    @DisplayName("refundPaidOrderByStudent는 Redis 복구가 실패해도 환불 응답은 정상 반환한다")
    void refundByStudent_redis_recovery_failure_does_not_fail_response() {
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Payment payment = createPaidPayment(paymentId, orderId, memberId, 50000L);
        Order order = createPaidOrder(orderId, memberId, courseId, 1, 50000L);
        Course course = createCourse(courseId, LocalDateTime.now().plusDays(10), 1);
        Refund refund = createRefund(payment, 50000L, 1);

        stubSuccessfulRefund(payment, order, course, refund, 0L);
        doAnswer(invocation -> {
            invocation.getArgument(0, Payment.class).refund();
            return null;
        }).when(paymentService).refund(any(Payment.class));
        given(orderService.applyRefund(orderId, memberId, 1)).willAnswer(invocation -> {
            order.applyRefund(1, LocalDateTime.now());
            return order;
        });
        doThrow(new RuntimeException("Redis unavailable"))
                .when(waitingListService).recoverCapacity(any(), any(), anyInt());

        RefundResponse result = refundFacade.refundPaidOrderByStudent(memberId, orderId, 1);

        assertThat(result).isNotNull();
        assertThat(result.refundPrice()).isEqualTo(50000L);
        verify(paymentService).refund(payment);
        verify(orderService).applyRefund(orderId, memberId, 1);
    }

    @Test
    @DisplayName("getMyRefund는 RefundService 결과를 그대로 반환한다")
    void getMyRefund_returns_service_result() {
        UUID memberId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        RefundDetailResponse expected = new RefundDetailResponse(
                refundId,
                UUID.randomUUID(),
                "Test Course",
                2,
                50000L,
                RefundReason.CANCEL,
                RefundStatus.COMPLETED,
                LocalDateTime.of(2026, 4, 21, 10, 0)
        );

        given(refundService.getMyRefund(refundId, memberId)).willReturn(expected);

        RefundDetailResponse result = refundFacade.getMyRefund(memberId, refundId);

        assertThat(result).isEqualTo(expected);
        verify(refundService).getMyRefund(refundId, memberId);
    }

    @Test
    @DisplayName("getAllMyRefunds는 RefundService 결과를 그대로 반환한다")
    void getAllMyRefunds_returns_service_result() {
        UUID memberId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        PageResponse<RefundListResponse> expected = new PageResponse<>(
                List.of(new RefundListResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Test Course",
                        1,
                        25000L,
                        RefundReason.CANCEL,
                        RefundStatus.COMPLETED,
                        LocalDateTime.of(2026, 4, 21, 11, 0)
                )),
                0,
                1,
                1L,
                10,
                true
        );

        given(refundService.getAllMyRefunds(memberId, RefundStatus.COMPLETED, pageable)).willReturn(expected);

        PageResponse<RefundListResponse> result =
                refundFacade.getAllMyRefunds(memberId, RefundStatus.COMPLETED, pageable);

        assertThat(result).isEqualTo(expected);
        verify(refundService).getAllMyRefunds(memberId, RefundStatus.COMPLETED, pageable);
    }

    @Test
    @DisplayName("refundAllPaidOrdersForCancelledCourse는 환불 대상이 없으면 빈 결과를 반환한다")
    void refundAllPaidOrdersForCancelledCourse_returns_empty_result_when_no_orders() {
        UUID courseId = UUID.randomUUID();
        Course course = createCourse(courseId, LocalDateTime.now().plusDays(10), 0);

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(orderRepository.findRefundableOrdersByCourseId(courseId)).willReturn(List.of());

        RefundCourseResponse result = refundFacade.refundAllPaidOrdersForCancelledCourse(courseId);

        assertThat(result.courseId()).isEqualTo(courseId);
        assertThat(result.courseTitle()).isEqualTo(course.getTitle());
        assertThat(result.totalOrderCount()).isZero();
        assertThat(result.refundedCount()).isZero();
        assertThat(result.failedCount()).isZero();
        assertThat(result.totalRefundAmount()).isZero();
    }

    @Test
    @DisplayName("refundAllPaidOrdersForCancelledCourse는 결제 완료 주문을 환불 처리한다")
    void refundAllPaidOrdersForCancelledCourse_refunds_paid_order() {
        UUID courseId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Course course = createCourse(courseId, LocalDateTime.now().plusDays(10), 2);
        Order order = createPaidOrder(orderId, memberId, courseId, 2, 50000L);
        Payment payment = createPaidPayment(paymentId, orderId, memberId, 100000L);

        stubInstructorRefundTarget(course, order, payment);

        RefundCourseResponse result = refundFacade.refundAllPaidOrdersForCancelledCourse(courseId);

        assertThat(result.totalOrderCount()).isEqualTo(1);
        assertThat(result.refundedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        assertThat(result.totalRefundAmount()).isEqualTo(100000L);

        PaymentGatewayRequest gatewayRequest = captureGatewayRequest();
        assertThat(gatewayRequest.pgKey()).isEqualTo(payment.getPgKey());
        assertThat(gatewayRequest.amount()).isEqualTo(100000L);
        assertThat(gatewayRequest.currentCancellableAmount()).isEqualTo(100000L);
        assertThat(gatewayRequest.reason()).isEqualTo("INSTRUCTOR");

        verify(refundService).createCompleted(payment, 100000L, 2, RefundReason.INSTRUCTOR);
        verify(paymentService).refund(payment);
        verify(orderService).cancelOrderForInstructor(orderId);
        verify(waitingListService).recoverCapacity(courseId, memberId, 2);
    }

    @Test
    @DisplayName("refundAllPaidOrdersForCancelledCourse는 게이트웨이 호출이 실패하면 실패 건수로 집계한다")
    void refundAllPaidOrdersForCancelledCourse_counts_failure_when_gateway_fails() {
        UUID courseId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Course course = createCourse(courseId, LocalDateTime.now().plusDays(10), 1);
        Order order = createPaidOrder(orderId, memberId, courseId, 1, 50000L);
        Payment payment = createPaidPayment(paymentId, orderId, memberId, 50000L);

        stubInstructorRefundTarget(course, order, payment);
        doThrow(new RuntimeException("gateway fail")).when(paymentGateway).cancelPayment(any());

        RefundCourseResponse result = refundFacade.refundAllPaidOrdersForCancelledCourse(courseId);

        assertThat(result.totalOrderCount()).isEqualTo(1);
        assertThat(result.refundedCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.totalRefundAmount()).isZero();
        verify(refundService).createFailed(paymentId, 50000L, 1, RefundReason.INSTRUCTOR);
        verify(refundService, never()).createCompleted(any(), anyLong(), anyInt(), eq(RefundReason.INSTRUCTOR));
        verify(orderService, never()).cancelOrderForInstructor(any());
        verify(waitingListService, never()).recoverCapacity(any(), any(), anyInt());
    }

    private void stubSuccessfulRefund(
            Payment payment,
            Order order,
            Course course,
            Refund refund,
            Long completedRefundTotal
    ) {
        given(paymentService.findByOrderId(order.getId())).willReturn(Optional.of(payment));
        given(paymentService.getByPgKeyForUpdate(payment.getPgKey())).willReturn(payment);
        given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));
        given(courseRepository.findById(course.getId())).willReturn(Optional.of(course));
        given(refundService.isRefundable(course.getStartAt())).willReturn(true);
        given(refundService.getCompletedRefundTotal(payment.getId())).willReturn(completedRefundTotal);
        lenient().when(refundService.createCompleted(eq(payment), anyLong(), anyInt(), eq(RefundReason.CANCEL)))
                .thenReturn(refund);
    }

    private void stubInstructorRefundTarget(Course course, Order order, Payment payment) {
        given(courseRepository.findById(course.getId())).willReturn(Optional.of(course));
        given(orderRepository.findRefundableOrdersByCourseId(course.getId())).willReturn(List.of(order));
        given(paymentService.findByOrderId(order.getId())).willReturn(Optional.of(payment));
        given(paymentService.getByPgKeyForUpdate(payment.getPgKey())).willReturn(payment);
        given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));
    }

    private PaymentGatewayRequest captureGatewayRequest() {
        ArgumentCaptor<PaymentGatewayRequest> captor = ArgumentCaptor.forClass(PaymentGatewayRequest.class);
        verify(paymentGateway).cancelPayment(captor.capture());
        return captor.getValue();
    }

    private Refund createRefund(Payment payment, Long amount, int cancelCount) {
        Refund refund = Refund.completed(payment, amount, cancelCount, RefundReason.CANCEL);
        ReflectionTestUtils.setField(refund, "id", UUID.randomUUID());
        return refund;
    }

    private Payment createPaidPayment(UUID paymentId, UUID orderId, UUID memberId, Long amount) {
        Payment payment = Payment.createPending(
                orderId,
                memberId,
                "pg-key-" + paymentId,
                amount,
                amount,
                PaymentPayWay.CARD
        );
        payment.confirmPaid();
        ReflectionTestUtils.setField(payment, "id", paymentId);
        return payment;
    }

    private Order createPaidOrder(UUID orderId, UUID memberId, UUID courseId, int orderCount, Long unitPrice) {
        Order order = Order.register(
                memberId,
                courseId,
                orderCount,
                BigInteger.valueOf(unitPrice),
                "Test Course"
        );
        order.completePayment();
        ReflectionTestUtils.setField(order, "id", orderId);
        return order;
    }

    private Course createCourse(UUID courseId, LocalDateTime startAt, int confirmCount) {
        Course course = CourseFixture.defaultCourse();
        ReflectionTestUtils.setField(course, "id", courseId);
        ReflectionTestUtils.setField(course, "startAt", startAt);
        ReflectionTestUtils.setField(course, "confirmCount", confirmCount);
        return course;
    }
}
