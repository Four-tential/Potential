package four_tential.potential.application.payment;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.entity.Refund;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.domain.payment.enums.RefundReason;
import four_tential.potential.domain.payment.enums.RefundStatus;
import four_tential.potential.domain.payment.repository.PaymentRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @InjectMocks
    private RefundService refundService;

    @Mock private RefundRepository refundRepository;
    @Mock private PaymentRepository paymentRepository;

    @Test
    @DisplayName("코스 시작 7일 초과 남고 PAID 상태이면 refundable = true 를 반환한다")
    void getRefundPreview_returns_refundable_true_when_over_7days() {
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Payment payment = createPaidPayment(paymentId, UUID.randomUUID(), memberId, 125000L);
        LocalDateTime startAt = LocalDateTime.now().plusDays(8);

        RefundPreviewResponse result = refundService.getRefundPreview(
                payment, memberId, "소도구 필라테스 입문반", startAt, 5, 25000L);

        assertThat(result.refundable()).isTrue();
        assertThat(result.refundPolicy()).isEqualTo("수강 일자 7일 전 취소 · 환불 가능");
        assertThat(result.currentOrderCount()).isEqualTo(5);
        assertThat(result.unitPrice()).isEqualTo(25000L);
        assertThat(result.paidTotalPrice()).isEqualTo(125000L);
        assertThat(result.paymentId()).isEqualTo(paymentId);
    }

    @Test
    @DisplayName("코스 시작 7일 이하이면 refundable = false 를 반환한다")
    void getRefundPreview_returns_refundable_false_when_within_7days() {
        UUID memberId = UUID.randomUUID();
        Payment payment = createPaidPayment(UUID.randomUUID(), UUID.randomUUID(), memberId, 125000L);

        RefundPreviewResponse result = refundService.getRefundPreview(
                payment, memberId, "테스트 강좌", LocalDateTime.now().plusDays(6), 5, 25000L);

        assertThat(result.refundable()).isFalse();
        assertThat(result.refundPolicy()).isEqualTo("수강 일자 7일 이내 취소 · 환불 불가");
    }

    @Test
    @DisplayName("타인의 결제이면 NOT_FOUND 예외가 발생한다")
    void getRefundPreview_throws_when_not_owner() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Payment payment = createPaidPayment(UUID.randomUUID(), UUID.randomUUID(), owner, 125000L);

        assertThatThrownBy(() -> refundService.getRefundPreview(
                payment, other, "테스트 강좌", LocalDateTime.now().plusDays(10), 5, 25000L))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT.getMessage());
    }

    @Test
    @DisplayName("PAID/PART_REFUNDED 가 아니면 환불 가능 상태 예외가 발생한다")
    void getRefundPreview_throws_when_payment_status_invalid() {
        UUID memberId = UUID.randomUUID();
        Payment payment = createPaymentWithStatus(UUID.randomUUID(), UUID.randomUUID(), memberId,
                125000L, PaymentStatus.PENDING);

        assertThatThrownBy(() -> refundService.getRefundPreview(
                payment, memberId, "테스트 강좌", LocalDateTime.now().plusDays(10), 5, 25000L))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(PaymentExceptionEnum.ERR_REFUND_PAYMENT_STATUS_INVALID.getMessage());
    }

    @Test
    @DisplayName("PART_REFUNDED 상태도 환불 가능 상태로 인정한다")
    void validateRefundablePaymentStatus_accepts_part_refunded() {
        Payment payment = createPaymentWithStatus(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                125000L, PaymentStatus.PART_REFUNDED);

        refundService.validateRefundablePaymentStatus(payment);
    }

    @Test
    @DisplayName("성공한 환불 금액 합계를 조회한다")
    void getCompletedRefundTotal_returns_repository_sum() {
        UUID paymentId = UUID.randomUUID();
        given(refundRepository.sumRefundPriceByPaymentIdAndStatus(paymentId, RefundStatus.COMPLETED))
                .willReturn(50000L);

        Long result = refundService.getCompletedRefundTotal(paymentId);

        assertThat(result).isEqualTo(50000L);
        verify(refundRepository).sumRefundPriceByPaymentIdAndStatus(paymentId, RefundStatus.COMPLETED);
    }

    @Test
    @DisplayName("성공 환불 이력을 COMPLETED 로 저장한다")
    void createCompleted_saves_completed_refund() {
        Payment payment = createPaidPayment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100000L);
        given(refundRepository.save(org.mockito.ArgumentMatchers.any(Refund.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Refund result = refundService.createCompleted(payment, 50000L, 2, RefundReason.CANCEL);

        assertThat(result.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(result.getCancelCount()).isEqualTo(2);
        assertThat(result.getRefundPrice()).isEqualTo(50000L);
        assertThat(result.getRefundedAt()).isNotNull();
    }

    @Test
    @DisplayName("실패 환불 이력을 FAILED 로 저장한다")
    void createFailed_saves_failed_refund() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = createPaidPayment(paymentId, UUID.randomUUID(), UUID.randomUUID(), 100000L);
        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));
        given(refundRepository.save(org.mockito.ArgumentMatchers.any(Refund.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Refund result = refundService.createFailed(paymentId, 50000L, 1, RefundReason.CANCEL);

        assertThat(result.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(result.getCancelCount()).isEqualTo(1);
        assertThat(result.getRefundedAt()).isNull();
    }

    private Payment createPaidPayment(UUID paymentId, UUID orderId, UUID memberId, Long amount) {
        return createPaymentWithStatus(paymentId, orderId, memberId, amount, PaymentStatus.PAID);
    }

    private Payment createPaymentWithStatus(
            UUID paymentId,
            UUID orderId,
            UUID memberId,
            Long amount,
            PaymentStatus targetStatus
    ) {
        Payment payment = Payment.createPending(
                orderId, memberId, "pg-key-" + paymentId,
                amount, amount, PaymentPayWay.CARD
        );

        switch (targetStatus) {
            case PAID -> payment.confirmPaid();
            case PART_REFUNDED -> {
                payment.confirmPaid();
                payment.partRefund();
            }
            case REFUNDED -> {
                payment.confirmPaid();
                payment.refund();
            }
            case FAILED -> payment.fail();
            default -> {
            }
        }

        ReflectionTestUtils.setField(payment, "id", paymentId);
        return payment;
    }
}
