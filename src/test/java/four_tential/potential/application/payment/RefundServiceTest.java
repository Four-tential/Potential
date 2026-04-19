package four_tential.potential.application.payment;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.domain.payment.repository.RefundRepository;
import four_tential.potential.presentation.payment.dto.RefundPreviewResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @InjectMocks
    private RefundService refundService;

    @Mock
    private RefundRepository refundRepository;

    @Test
    @DisplayName("코스 시작 7일 초과 남고 PAID 상태이면 refundable = true 를 반환한다")
    void getRefundPreview_returns_refundable_true_when_over_7days() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();

        Payment payment = createPaidPayment(paymentId, orderId, memberId, 125000L);
        LocalDateTime startAt = LocalDateTime.now().plusDays(8);

        RefundPreviewResponse result = refundService.getRefundPreview(
                payment, memberId, "소도구 필라테스 입문반", startAt, 5);

        assertThat(result.refundable()).isTrue();
        assertThat(result.refundPolicy()).isEqualTo("수강 일자 7일 전 취소 · 환불 가능");
        assertThat(result.currentOrderCount()).isEqualTo(5);
        assertThat(result.unitPrice()).isEqualTo(25000L);       // 125000 / 5
        assertThat(result.paidTotalPrice()).isEqualTo(125000L);
        assertThat(result.courseTitle()).isEqualTo("소도구 필라테스 입문반");
        assertThat(result.paymentId()).isEqualTo(paymentId);
        assertThat(result.startAt()).isEqualTo(startAt);
    }

    @Test
    @DisplayName("PART_REFUNDED 상태이고 7일 초과 남으면 refundable = true 를 반환한다")
    void getRefundPreview_part_refunded_is_refundable_when_over_7days() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();

        // 5장 주문 후 2장 부분 환불 → Order.orderCount 는 이미 3 으로 차감된 상태
        Payment payment = createPaymentWithStatus(paymentId, orderId, memberId,
                125000L, PaymentStatus.PART_REFUNDED);

        RefundPreviewResponse result = refundService.getRefundPreview(
                payment, memberId, "테스트 강좌", LocalDateTime.now().plusDays(10), 3);

        assertThat(result.refundable()).isTrue();
        assertThat(result.currentOrderCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("코스 시작 7일 이하 남으면 refundable = false 를 반환한다")
    void getRefundPreview_returns_refundable_false_when_within_7days() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();

        Payment payment = createPaidPayment(paymentId, orderId, memberId, 125000L);

        RefundPreviewResponse result = refundService.getRefundPreview(
                payment, memberId, "테스트 강좌", LocalDateTime.now().plusDays(6), 5);

        assertThat(result.refundable()).isFalse();
        assertThat(result.refundPolicy()).isEqualTo("수강 일자 7일 이내 취소 · 환불 불가");
    }

    @Test
    @DisplayName("코스 시작일이 오늘이면 당일 취소 불가로 refundable = false 를 반환한다")
    void getRefundPreview_returns_refundable_false_when_same_day() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();

        Payment payment = createPaidPayment(paymentId, orderId, memberId, 125000L);
        // 오늘 저녁 시작 — 당일 취소 불가
        LocalDateTime todayEvening = LocalDateTime.now().withHour(20).withMinute(0).withSecond(0);

        RefundPreviewResponse result = refundService.getRefundPreview(
                payment, memberId, "테스트 강좌", todayEvening, 5);

        assertThat(result.refundable()).isFalse();
    }

    @Test
    @DisplayName("코스가 이미 시작됐으면 refundable = false 를 반환한다")
    void getRefundPreview_returns_refundable_false_when_course_already_started() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();

        Payment payment = createPaidPayment(paymentId, orderId, memberId, 125000L);

        RefundPreviewResponse result = refundService.getRefundPreview(
                payment, memberId, "테스트 강좌", LocalDateTime.now().minusDays(1), 5);

        assertThat(result.refundable()).isFalse();
    }

    @Test
    @DisplayName("정확히 7일 전(마감 시각과 동일)이면 refundable = false 를 반환한다")
    void getRefundPreview_returns_refundable_false_when_exactly_7days() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();

        Payment payment = createPaidPayment(paymentId, orderId, memberId, 125000L);
        // startAt = 지금으로부터 정확히 7일 후 → refundDeadline = 지금 → isBefore(now) = false
        LocalDateTime startAt = LocalDateTime.now().plusDays(7);

        RefundPreviewResponse result = refundService.getRefundPreview(
                payment, memberId, "테스트 강좌", startAt, 5);

        assertThat(result.refundable()).isFalse();
    }

    @Test
    @DisplayName("currentOrderCount 는 Facade 에서 전달받은 orderCount 를 그대로 반환한다")
    void getRefundPreview_currentOrderCount_equals_passed_orderCount() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();

        Payment payment = createPaidPayment(paymentId, orderId, memberId, 50000L);

        RefundPreviewResponse result = refundService.getRefundPreview(
                payment, memberId, "테스트 강좌", LocalDateTime.now().plusDays(10), 2);

        assertThat(result.currentOrderCount()).isEqualTo(2);
        assertThat(result.unitPrice()).isEqualTo(25000L);       // 50000 / 2
    }

    @Test
    @DisplayName("타인의 결제이면 NOT_FOUND 예외가 발생한다")
    void getRefundPreview_throws_when_not_owner() {
        UUID owner     = UUID.randomUUID();
        UUID other     = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();

        Payment payment = createPaidPayment(paymentId, orderId, owner, 125000L);

        assertThatThrownBy(() -> refundService.getRefundPreview(
                payment, other, "테스트 강좌", LocalDateTime.now().plusDays(10), 5))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("PENDING 상태 결제는 환불 조회 시 예외가 발생한다")
    void getRefundPreview_throws_when_payment_is_pending() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();

        Payment payment = createPaymentWithStatus(paymentId, orderId, memberId,
                125000L, PaymentStatus.PENDING);

        assertThatThrownBy(() -> refundService.getRefundPreview(
                payment, memberId, "테스트 강좌", LocalDateTime.now().plusDays(10), 5))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("FAILED 상태 결제는 환불 조회 시 예외가 발생한다")
    void getRefundPreview_throws_when_payment_is_failed() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();

        Payment payment = createPaymentWithStatus(paymentId, orderId, memberId, 125000L, PaymentStatus.FAILED);

        assertThatThrownBy(() -> refundService.getRefundPreview(
                payment, memberId, "테스트 강좌", LocalDateTime.now().plusDays(10), 5))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("REFUNDED 상태 결제는 환불 조회 시 예외가 발생한다")
    void getRefundPreview_throws_when_payment_is_fully_refunded() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();

        Payment payment = createPaidPayment(paymentId, orderId, memberId, 125000L);
        payment.refund();

        assertThatThrownBy(() -> refundService.getRefundPreview(
                payment, memberId, "테스트 강좌", LocalDateTime.now().plusDays(10), 5))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("refundPolicy — refundable = true 이면 '수강 일자 7일 전 취소 · 환불 가능' 문구를 반환한다")
    void getRefundPreview_refundPolicy_is_refundable_text_when_true() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();

        Payment payment = createPaidPayment(paymentId, orderId, memberId, 125000L);

        RefundPreviewResponse result = refundService.getRefundPreview(
                payment, memberId, "테스트 강좌", LocalDateTime.now().plusDays(8), 5);

        assertThat(result.refundPolicy()).isEqualTo("수강 일자 7일 전 취소 · 환불 가능");
    }

    @Test
    @DisplayName("refundPolicy — refundable = false 이면 '수강 일자 7일 이내 취소 · 환불 불가' 문구를 반환한다")
    void getRefundPreview_refundPolicy_is_not_refundable_text_when_false() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();

        Payment payment = createPaidPayment(paymentId, orderId, memberId, 125000L);

        RefundPreviewResponse result = refundService.getRefundPreview(
                payment, memberId, "테스트 강좌", LocalDateTime.now().plusDays(3), 5);

        assertThat(result.refundPolicy()).isEqualTo("수강 일자 7일 이내 취소 · 환불 불가");
    }

    @Test
    @DisplayName("unitPrice — paidTotalPrice 를 orderCount 로 나눈 값을 반환한다")
    void getRefundPreview_unitPrice_is_paidTotalPrice_divided_by_orderCount() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();

        Payment payment = createPaidPayment(paymentId, orderId, memberId, 150000L);

        RefundPreviewResponse result = refundService.getRefundPreview(
                payment, memberId, "테스트 강좌", LocalDateTime.now().plusDays(10), 3);

        // 150000 / 3 = 50000
        assertThat(result.unitPrice()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("startAt — 응답의 startAt 이 전달받은 courseStartAt 과 동일하다")
    void getRefundPreview_startAt_equals_courseStartAt() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();
        LocalDateTime startAt = LocalDateTime.of(2025, 8, 15, 14, 0);

        Payment payment = createPaidPayment(paymentId, orderId, memberId, 125000L);

        RefundPreviewResponse result = refundService.getRefundPreview(
                payment, memberId, "테스트 강좌", startAt, 5);

        assertThat(result.startAt()).isEqualTo(startAt);
    }

    @Test
    @DisplayName("courseTitle — 응답의 courseTitle 이 전달받은 값과 동일하다")
    void getRefundPreview_courseTitle_equals_passed_value() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();

        Payment payment = createPaidPayment(paymentId, orderId, memberId, 125000L);

        RefundPreviewResponse result = refundService.getRefundPreview(
                payment, memberId, "소도구 필라테스 입문반",
                LocalDateTime.now().plusDays(10), 5);

        assertThat(result.courseTitle()).isEqualTo("소도구 필라테스 입문반");
    }

    @Test
    @DisplayName("내일 시작하는 코스는 당일이 아니지만 7일 이하이므로 refundable = false 를 반환한다")
    void getRefundPreview_returns_false_when_course_starts_tomorrow() {
        UUID memberId  = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();

        Payment payment = createPaidPayment(paymentId, orderId, memberId, 125000L);

        RefundPreviewResponse result = refundService.getRefundPreview(
                payment, memberId, "테스트 강좌",
                LocalDateTime.now().plusDays(1), 5);

        assertThat(result.refundable()).isFalse();
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

    private Payment createPaymentWithStatus(UUID paymentId, UUID orderId, UUID memberId,
                                            Long amount, PaymentStatus targetStatus) {
        Payment payment = Payment.createPending(
                orderId, memberId, null, "pg-key-1",
                amount, 0L, amount, PaymentPayWay.CARD
        );

        switch (targetStatus) {
            case PAID         -> payment.confirmPaid();
            case PART_REFUNDED -> { payment.confirmPaid(); payment.partRefund(); }
            case REFUNDED     -> { payment.confirmPaid(); payment.refund(); }
            case FAILED       -> payment.fail();
            default           -> { /* PENDING 그대로 */ }
        }

        ReflectionTestUtils.setField(payment, "id", paymentId);
        return payment;
    }
}