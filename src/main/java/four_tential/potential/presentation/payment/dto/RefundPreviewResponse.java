package four_tential.potential.presentation.payment.dto;

import four_tential.potential.application.payment.consts.RefundConstants;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 환불 가능 여부 조회 응답 DTO
 *
 * currentOrderCount : Order.orderCount (부분 환불 시 차감 업데이트된 잔여 수강권 수량)
 * unitPrice         : 수강권 1개당 금액 (Order.priceSnap)
 * refundable        : 환불 가능 여부
 *                     PAID/PART_REFUNDED 상태이고, 당일 취소가 아니며,
 *                     코스 시작 7일 초과 남아 있어야 true
 * refundPolicy      : 사용자에게 보여줄 환불 정책 문구
 *
 */
public record RefundPreviewResponse(
        UUID paymentId,
        String courseTitle,
        LocalDateTime startAt,
        int currentOrderCount,
        Long unitPrice,
        Long paidTotalPrice,
        boolean refundable,
        String refundPolicy
) {

    public static RefundPreviewResponse of(
            UUID paymentId,
            String courseTitle,
            LocalDateTime startAt,
            int currentOrderCount,
            Long unitPrice,
            Long paidTotalPrice,
            boolean refundable
    ) {
        return new RefundPreviewResponse(
                paymentId,
                courseTitle,
                startAt,
                currentOrderCount,
                unitPrice,
                paidTotalPrice,
                refundable,
                refundable
                        ? RefundConstants.REFUND_POLICY_REFUNDABLE
                        : RefundConstants.REFUND_POLICY_NOT_REFUNDABLE
        );
    }
}
