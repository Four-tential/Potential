package four_tential.potential.presentation.payment.dto;

import four_tential.potential.domain.payment.enums.PaymentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentListResponse(
        UUID paymentId,
        UUID orderId,
        String courseTitle,
        int orderCount,
        Long paidTotalPrice,
        PaymentStatus status,
        LocalDateTime paidAt
) {
}
