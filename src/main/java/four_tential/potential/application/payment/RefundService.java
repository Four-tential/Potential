package four_tential.potential.application.payment;

import four_tential.potential.application.payment.consts.RefundConstants;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.entity.Refund;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.domain.payment.enums.RefundReason;
import four_tential.potential.domain.payment.enums.RefundStatus;
import four_tential.potential.domain.payment.repository.PaymentRepository;
import four_tential.potential.domain.payment.repository.RefundRepository;
import four_tential.potential.presentation.payment.dto.RefundDetailResponse;
import four_tential.potential.presentation.payment.dto.RefundPreviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;

    /**
     * 환불 가능 여부와 예상 환불 정보를 만든다.
     * 단가는 결제 금액을 현재 수량으로 다시 나누지 않고, 주문의 1장 가격 스냅샷을 사용한다.
     */
    public RefundPreviewResponse getRefundPreview(
            Payment payment,
            UUID memberId,
            String courseTitle,
            LocalDateTime courseStartAt,
            int currentOrderCount,
            Long unitPrice
    ) {
        validateOwner(payment, memberId);
        validateRefundablePaymentStatus(payment);

        boolean refundable = isRefundable(courseStartAt);

        return RefundPreviewResponse.of(
                payment.getId(),
                courseTitle,
                courseStartAt,
                currentOrderCount,
                unitPrice,
                payment.getPaidTotalPrice(),
                refundable
        );
    }

    /**
     * 이미 성공한 환불 금액 합계를 구한다.
     * 여러 번 부분 환불해도 남은 환불 가능 금액을 넘지 않게 막기 위해 사용한다.
     */
    public Long getCompletedRefundTotal(UUID paymentId) {
        return refundRepository.sumRefundPriceByPaymentIdAndStatus(paymentId, RefundStatus.COMPLETED);
    }

    /**
     * 환불 단건을 조회한다.
     */
    public RefundDetailResponse getMyRefund(UUID refundId, UUID memberId) {
        return refundRepository.findDetailByIdAndMemberId(refundId, memberId)
                .orElseThrow(() -> new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_REFUND));
    }

    /**
     * PortOne 환불 성공 후 COMPLETED 이력을 남긴다.
     * 결제 상태 변경과 주문 수량 차감과 같은 트랜잭션에서 호출한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Refund createCompleted(Payment payment, Long refundPrice, int cancelCount, RefundReason reason) {
        return refundRepository.save(Refund.completed(payment, refundPrice, cancelCount, reason));
    }

    /**
     * PortOne 환불 실패도 기록으로 남긴다.
     * 바깥 트랜잭션과 별도로 저장해야 운영자가 실패 이력을 확인할 수 있다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Refund createFailed(UUID paymentId, Long refundPrice, int cancelCount, RefundReason reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT));
        return refundRepository.save(Refund.failed(payment, refundPrice, cancelCount, reason));
    }

    /**
     * 환불 요청자가 결제 주인인지 확인한다.
     */
    public void validateOwner(Payment payment, UUID memberId) {
        if (!payment.getMemberId().equals(memberId)) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT);
        }
    }

    /**
     * 수강생 환불은 PAID 또는 PART_REFUNDED 결제에서만 가능하다.
     */
    public void validateRefundablePaymentStatus(Payment payment) {
        PaymentStatus status = payment.getStatus();
        if (status != PaymentStatus.PAID && status != PaymentStatus.PART_REFUNDED) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_REFUND_PAYMENT_STATUS_INVALID);
        }
    }

    /**
     * 코스 시작 7일 초과 전까지만 환불 가능하다.
     * 당일 취소는 무조건 불가로 본다.
     */
    public boolean isRefundable(LocalDateTime courseStartAt) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = courseStartAt.toLocalDate();

        if (today.isEqual(startDate)) {
            return false;
        }

        LocalDateTime refundDeadline = courseStartAt.minusDays(RefundConstants.REFUND_DEADLINE_DAYS);
        return LocalDateTime.now().isBefore(refundDeadline);
    }
}
