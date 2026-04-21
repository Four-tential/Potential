package four_tential.potential.presentation.payment.dto;

import four_tential.potential.domain.payment.entity.Refund;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.domain.payment.enums.RefundReason;
import four_tential.potential.domain.payment.enums.RefundStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 환불 조회용 응답 DTO
 */
public record RefundResponse(
        UUID refundId,
        UUID paymentId,
        String courseTitle,
        int cancelCount,
        Long unitPrice,
        Long refundPrice,
        int remainingCount,
        RefundReason reason,
        RefundStatus status,
        LocalDateTime refundedAt,
        PaymentStatus paymentStatus
) {

    public static RefundResponse of(
            Refund refund,
            UUID paymentId,
            String courseTitle,
            int remainingCount,
            Long unitPrice,
            PaymentStatus paymentStatus
    ) {
        return new RefundResponse(
                refund.getId(),
                paymentId,
                courseTitle,
                refund.getCancelCount(),
                unitPrice,
                refund.getRefundPrice(),
                remainingCount,
                refund.getReason(),
                refund.getStatus(),
                refund.getRefundedAt(),
                paymentStatus
        );
    }
}
