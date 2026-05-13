package four_tential.potential.domain.payment.repository;

import java.math.BigInteger;
import java.util.UUID;

/**
 * 환불 가능 여부 조회를 위한 projection record
 */
public record RefundPreviewData(
        UUID paymentId,
        UUID memberId,
        Long paidTotalPrice,
        String paymentStatusName,   // PaymentStatus.name()
        UUID courseId,              // course 조회 시 사용
        String courseTitle,         // order.titleSnap
        int currentOrderCount,      // order.orderCount
        BigInteger priceSnap        // order.priceSnap (단가 계산용)
) {}