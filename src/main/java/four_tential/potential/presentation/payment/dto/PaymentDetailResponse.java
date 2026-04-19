package four_tential.potential.presentation.payment.dto;

import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.PaymentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentDetailResponse(
        UUID paymentId,
        UUID orderId,
        String courseTitle,
        int orderCount,
        Long totalPrice,
        Long discountPrice,
        Long paidTotalPrice,
        PaymentPayWay payWay,
        PaymentStatus status,
        LocalDateTime paidAt
) {
}
