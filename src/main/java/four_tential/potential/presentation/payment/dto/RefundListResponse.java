package four_tential.potential.presentation.payment.dto;

import four_tential.potential.domain.payment.enums.RefundReason;
import four_tential.potential.domain.payment.enums.RefundStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record RefundListResponse(
        UUID refundId,
        UUID paymentId,
        String courseTitle,
        int cancelCount,
        Long refundPrice,
        RefundReason reason,
        RefundStatus status,
        LocalDateTime refundedAt
) {}
