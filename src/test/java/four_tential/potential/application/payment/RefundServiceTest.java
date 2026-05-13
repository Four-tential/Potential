package four_tential.potential.application.payment;

import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.entity.Refund;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.domain.payment.enums.RefundReason;
import four_tential.potential.domain.payment.enums.RefundStatus;
import four_tential.potential.domain.payment.repository.PaymentRepository;
import four_tential.potential.domain.payment.repository.RefundPreviewData;
import four_tential.potential.domain.payment.repository.RefundRepository;
import four_tential.potential.presentation.payment.dto.RefundDetailResponse;
import four_tential.potential.presentation.payment.dto.RefundListResponse;
import four_tential.potential.presentation.payment.dto.RefundPreviewResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @InjectMocks
    private RefundService refundService;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("getRefundPreview는 코스 시작이 환불 마감일 이후면 refundable=true를 반환한다")
    void getRefundPreview_returns_refundable_true() {
        UUID paymentId = UUID.randomUUID();
        RefundPreviewData data = createRefundPreviewData(
                paymentId,
                PaymentStatus.PAID.name(),
                125000L,
                5,
                BigInteger.valueOf(25000L)
        );

        RefundPreviewResponse result = refundService.getRefundPreview(
                data,
                LocalDateTime.now().plusDays(8)
        );

        assertThat(result.paymentId()).isEqualTo(paymentId);
        assertThat(result.currentOrderCount()).isEqualTo(5);
        assertThat(result.unitPrice()).isEqualTo(25000L);
        assertThat(result.paidTotalPrice()).isEqualTo(125000L);
        assertThat(result.refundable()).isTrue();
    }

    @Test
    @DisplayName("getRefundPreview는 코스 시작이 환불 마감일 이내면 refundable=false를 반환한다")
    void getRefundPreview_returns_refundable_false() {
        RefundPreviewData data = createRefundPreviewData(
                UUID.randomUUID(),
                PaymentStatus.PAID.name(),
                125000L,
                5,
                BigInteger.valueOf(25000L)
        );

        RefundPreviewResponse result = refundService.getRefundPreview(
                data,
                LocalDateTime.now().plusDays(6)
        );

        assertThat(result.refundable()).isFalse();
    }

    @Test
    @DisplayName("getRefundPreview는 환불 가능한 결제 상태가 아니면 예외가 발생한다")
    void getRefundPreview_throws_when_payment_status_invalid() {
        RefundPreviewData data = createRefundPreviewData(
                UUID.randomUUID(),
                PaymentStatus.PENDING.name(),
                125000L,
                5,
                BigInteger.valueOf(25000L)
        );

        assertThatThrownBy(() -> refundService.getRefundPreview(data, LocalDateTime.now().plusDays(10)))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(PaymentExceptionEnum.ERR_REFUND_PAYMENT_STATUS_INVALID.getMessage());
    }

    @Test
    @DisplayName("getRefundPreview는 priceSnap이 long 범위를 넘으면 예외가 발생한다")
    void getRefundPreview_throws_when_unit_price_overflows_long() {
        RefundPreviewData data = createRefundPreviewData(
                UUID.randomUUID(),
                PaymentStatus.PAID.name(),
                125000L,
                5,
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)
        );

        assertThatThrownBy(() -> refundService.getRefundPreview(data, LocalDateTime.now().plusDays(10)))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(PaymentExceptionEnum.ERR_PAYMENT_AMOUNT_MISMATCH.getMessage());
    }

    @Test
    @DisplayName("validateRefundablePaymentStatus는 PART_REFUNDED 상태를 허용한다")
    void validateRefundablePaymentStatus_accepts_part_refunded() {
        Payment payment = createPaymentWithStatus(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                125000L,
                PaymentStatus.PART_REFUNDED
        );

        refundService.validateRefundablePaymentStatus(payment);
    }

    @Test
    @DisplayName("getCompletedRefundTotal은 저장소에서 COMPLETED 환불 합계를 조회한다")
    void getCompletedRefundTotal_returns_repository_sum() {
        UUID paymentId = UUID.randomUUID();
        given(refundRepository.sumRefundPriceByPaymentIdAndStatus(paymentId, RefundStatus.COMPLETED))
                .willReturn(50000L);

        Long result = refundService.getCompletedRefundTotal(paymentId);

        assertThat(result).isEqualTo(50000L);
        verify(refundRepository).sumRefundPriceByPaymentIdAndStatus(paymentId, RefundStatus.COMPLETED);
    }

    @Test
    @DisplayName("createCompleted는 COMPLETED 환불 이력을 저장한다")
    void createCompleted_saves_completed_refund() {
        Payment payment = createPaidPayment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100000L);
        given(refundRepository.save(any(Refund.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Refund result = refundService.createCompleted(payment, 50000L, 2, RefundReason.CANCEL);

        assertThat(result.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(result.getCancelCount()).isEqualTo(2);
        assertThat(result.getRefundPrice()).isEqualTo(50000L);
        assertThat(result.getRefundedAt()).isNotNull();
    }

    @Test
    @DisplayName("createFailed는 FAILED 환불 이력을 저장한다")
    void createFailed_saves_failed_refund() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = createPaidPayment(paymentId, UUID.randomUUID(), UUID.randomUUID(), 100000L);
        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));
        given(refundRepository.save(any(Refund.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Refund result = refundService.createFailed(paymentId, 50000L, 1, RefundReason.CANCEL);

        assertThat(result.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(result.getCancelCount()).isEqualTo(1);
        assertThat(result.getRefundedAt()).isNull();
    }

    @Test
    @DisplayName("getMyRefund는 회원 본인의 환불 상세를 반환한다")
    void getMyRefund_returns_detail() {
        UUID refundId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        RefundDetailResponse expected = new RefundDetailResponse(
                refundId,
                UUID.randomUUID(),
                "Test Course",
                2,
                50000L,
                RefundReason.CANCEL,
                RefundStatus.COMPLETED,
                LocalDateTime.of(2026, 4, 21, 9, 0)
        );
        given(refundRepository.findDetailByIdAndMemberId(refundId, memberId)).willReturn(Optional.of(expected));

        RefundDetailResponse result = refundService.getMyRefund(refundId, memberId);

        assertThat(result).isEqualTo(expected);
        verify(refundRepository).findDetailByIdAndMemberId(refundId, memberId);
    }

    @Test
    @DisplayName("getMyRefund는 회원 본인의 환불이 아니면 예외가 발생한다")
    void getMyRefund_throws_when_not_found() {
        UUID refundId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        given(refundRepository.findDetailByIdAndMemberId(refundId, memberId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> refundService.getMyRefund(refundId, memberId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(PaymentExceptionEnum.ERR_NOT_FOUND_REFUND.getMessage());
    }

    @Test
    @DisplayName("getAllMyRefunds는 저장소 결과를 PageResponse로 감싸서 반환한다")
    void getAllMyRefunds_returns_page_response() {
        UUID memberId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        RefundListResponse content = new RefundListResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Test Course",
                1,
                25000L,
                RefundReason.CANCEL,
                RefundStatus.COMPLETED,
                LocalDateTime.of(2026, 4, 21, 9, 30)
        );
        PageImpl<RefundListResponse> page = new PageImpl<>(List.of(content), pageable, 1);
        given(refundRepository.findListByMemberIdAndStatus(memberId, RefundStatus.COMPLETED, pageable))
                .willReturn(page);

        PageResponse<RefundListResponse> result =
                refundService.getAllMyRefunds(memberId, RefundStatus.COMPLETED, pageable);

        assertThat(result.content()).containsExactly(content);
        assertThat(result.currentPage()).isZero();
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.isLast()).isTrue();
        verify(refundRepository).findListByMemberIdAndStatus(memberId, RefundStatus.COMPLETED, pageable);
    }

    private RefundPreviewData createRefundPreviewData(
            UUID paymentId,
            String paymentStatusName,
            long paidTotalPrice,
            int currentOrderCount,
            BigInteger priceSnap
    ) {
        return new RefundPreviewData(
                paymentId,
                UUID.randomUUID(),
                paidTotalPrice,
                paymentStatusName,
                UUID.randomUUID(),
                "Test Course",
                currentOrderCount,
                priceSnap
        );
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
