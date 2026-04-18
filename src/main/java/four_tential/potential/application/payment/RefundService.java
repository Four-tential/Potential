package four_tential.potential.application.payment;

import four_tential.potential.application.payment.consts.RefundConstants;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.domain.payment.repository.RefundRepository;
import four_tential.potential.presentation.payment.dto.RefundPreviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundService {

    private final RefundRepository refundRepository;

    /**
     * 환불 가능 여부 및 예상 환불 정보 조회
     */
    public RefundPreviewResponse getRefundPreview(
            Payment payment,
            UUID memberId,
            String courseTitle,
            LocalDateTime courseStartAt,
            int currentOrderCount
    ) {
        validateOwner(payment, memberId);
        validateRefundablePaymentStatus(payment);

        boolean refundable = isRefundable(courseStartAt);
        long unitPrice = payment.getPaidTotalPrice() / currentOrderCount;

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
     * 결제 소유자 검증
     */
    private void validateOwner(Payment payment, UUID memberId) {
        if (!payment.getMemberId().equals(memberId)) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT);
        }
    }

    /**
     * 환불 조회 가능한 결제 상태 검증
     */
    private void validateRefundablePaymentStatus(Payment payment) {
        PaymentStatus status = payment.getStatus();
        if (status != PaymentStatus.PAID && status != PaymentStatus.PART_REFUNDED) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_REFUND_NOT_ALLOWED);
        }
    }

    /**
     * 환불 가능 여부 판단
     */
    private boolean isRefundable(LocalDateTime courseStartAt) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = courseStartAt.toLocalDate();

        if (today.isEqual(startDate)) {
            return false;
        }

        LocalDateTime refundDeadline = courseStartAt.minusDays(RefundConstants.REFUND_DEADLINE_DAYS);
        return LocalDateTime.now().isBefore(refundDeadline);
    }

}
