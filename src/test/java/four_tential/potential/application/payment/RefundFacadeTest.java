package four_tential.potential.application.payment;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.fixture.CourseFixture;
import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.port.PaymentGateway;
import four_tential.potential.presentation.payment.dto.RefundPreviewResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
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
    private WebhookService webhookService;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CourseRepository courseRepository;

    @Test
    @DisplayName("payment, order, course 를 조회 후 응답을 반환한다")
    void getRefundPreview_returns_response_from_service() {
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        LocalDateTime startAt = LocalDateTime.now().plusDays(8);

        Payment payment = createPaidPayment(paymentId, orderId, memberId, 125000L);
        Order order = createOrder(orderId, memberId, courseId, 5);
        Course course = createCourse(courseId, startAt);

        RefundPreviewResponse expected = new RefundPreviewResponse(
                paymentId, "테스트 강좌", startAt,
                5, 25000L, 125000L,
                true, "7일 전 취소 · 전액 환불"
        );

        given(paymentService.getById(paymentId)).willReturn(payment);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(refundService.getRefundPreview(
                eq(payment), eq(memberId),
                eq("테스트 강좌"), eq(startAt), eq(5)
        )).willReturn(expected);

        RefundPreviewResponse result = refundFacade.getRefundPreview(memberId, paymentId);

        assertThat(result).isEqualTo(expected);
        verify(paymentService).getById(paymentId);
        verify(orderRepository).findById(orderId);
        verify(courseRepository).findById(courseId);
        verify(refundService).getRefundPreview(payment, memberId, "테스트 강좌", startAt, 5);
    }

    @Test
    @DisplayName("RefundService 에 order.titleSnap, course.startAt, order.orderCount 를 정확히 전달한다")
    void getRefundPreview_passes_correct_values_to_service() {
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        LocalDateTime startAt = LocalDateTime.of(2025, 6, 1, 10, 0);

        Payment payment = createPaidPayment(paymentId, orderId, memberId, 50000L);
        Order order = createOrder(orderId, memberId, courseId, 2);
        Course course = createCourse(courseId, startAt);
        RefundPreviewResponse stub = new RefundPreviewResponse(
                paymentId, "테스트 강좌", startAt, 2, 25000L, 50000L,
                true, "7일 전 취소 · 전액 환불"
        );

        given(paymentService.getById(paymentId)).willReturn(payment);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(refundService.getRefundPreview(any(), any(), any(), any(), eq(2))).willReturn(stub);

        refundFacade.getRefundPreview(memberId, paymentId);

        // titleSnap, startAt, orderCount 가 정확히 전달됐는지 검증
        verify(refundService).getRefundPreview(payment, memberId, "테스트 강좌", startAt, 2);
    }

    @Test
    @DisplayName("결제가 없으면 NOT_FOUND 예외가 발생한다")
    void getRefundPreview_throws_when_payment_not_found() {
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        given(paymentService.getById(paymentId))
                .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT));

        assertThatThrownBy(() -> refundFacade.getRefundPreview(memberId, paymentId))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("주문이 없으면 NOT_FOUND 예외가 발생한다")
    void getRefundPreview_throws_when_order_not_found() {
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Payment payment = createPaidPayment(paymentId, orderId, memberId, 125000L);
        given(paymentService.getById(paymentId)).willReturn(payment);
        given(orderRepository.findById(orderId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> refundFacade.getRefundPreview(memberId, paymentId))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("코스가 없으면 NOT_FOUND 예외가 발생한다")
    void getRefundPreview_throws_when_course_not_found() {
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        Payment payment = createPaidPayment(paymentId, orderId, memberId, 125000L);
        Order order = createOrder(orderId, memberId, courseId, 5);
        given(paymentService.getById(paymentId)).willReturn(payment);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> refundFacade.getRefundPreview(memberId, paymentId))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("RefundService 에서 예외가 발생하면 그대로 전파된다")
    void getRefundPreview_propagates_service_exception() {
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        Payment payment = createPaidPayment(paymentId, orderId, memberId, 125000L);
        Order order = createOrder(orderId, memberId, courseId, 5);
        Course course = createCourse(courseId, LocalDateTime.now().plusDays(8));
        given(paymentService.getById(paymentId)).willReturn(payment);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(refundService.getRefundPreview(any(), any(), any(), any(), anyInt()))
                .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_REFUND_NOT_ALLOWED));

        assertThatThrownBy(() -> refundFacade.getRefundPreview(memberId, paymentId))
                .isInstanceOf(ServiceErrorException.class);
    }

    private Payment createPaidPayment(UUID paymentId, UUID orderId, UUID memberId, Long amount) {
        Payment payment = Payment.createPending(
                orderId, memberId, null, "pg-key-1",
                amount, 0L, amount, PaymentPayWay.CARD
        );
        payment.confirmPaid();
        ReflectionTestUtils.setField(payment, "id", paymentId);
        return payment;
    }

    private Order createOrder(UUID orderId, UUID memberId, UUID courseId, int orderCount) {
        Order order = Order.register(
                memberId, courseId, orderCount,
                BigInteger.valueOf(25000), "테스트 강좌"
        );
        ReflectionTestUtils.setField(order, "id", orderId);
        return order;
    }

    private Course createCourse(UUID courseId, LocalDateTime startAt) {
        Course course = CourseFixture.defaultCourse();
        ReflectionTestUtils.setField(course, "id", courseId);
        ReflectionTestUtils.setField(course, "startAt", startAt);
        return course;
    }
}