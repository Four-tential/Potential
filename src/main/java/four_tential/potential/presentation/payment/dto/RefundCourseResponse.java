package four_tential.potential.presentation.payment.dto;

import java.util.UUID;

/**
 * 강사 코스 취소 시 일괄 환불 처리 응답 DTO
 */
public record RefundCourseResponse(
        UUID courseId,
        String courseTitle,
        int totalOrderCount,
        int refundedCount,
        int failedCount,
        Long totalRefundAmount
) {
    public static RefundCourseResponse of(
            UUID courseId,
            String courseTitle,
            int totalOrderCount,
            int refundedCount,
            int failedCount,
            Long totalRefundAmount
    ) {
        return new RefundCourseResponse(
                courseId, courseTitle, totalOrderCount,
                refundedCount, failedCount, totalRefundAmount
        );
    }
}
