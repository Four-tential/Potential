package four_tential.potential.presentation.payment;

import four_tential.potential.application.payment.RefundFacade;
import four_tential.potential.domain.payment.entity.CourseCancelOutbox;
import four_tential.potential.domain.payment.repository.CourseCancelOutboxRepository;
import four_tential.potential.presentation.payment.dto.RefundCourseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 강사 코스 취소 일괄 환불 임시 테스트용 컨트롤러
 */
@RestController
@RequestMapping("/v1/refund")
@RequiredArgsConstructor
public class RefundTestController {

    private final RefundFacade refundFacade;

    // MVP 방식
    @PostMapping("/courses/{courseId}/instructor-cancel/immediate")
    public RefundCourseResponse testInstructorCancelRefund(
            @PathVariable UUID courseId
    ) {
        return refundFacade.refundAllPaidOrdersForCancelledCourse(courseId);
    }

    // 고도화 방식. Outbox에 환불 예약 저장
    @PostMapping("/courses/{courseId}/instructor-cancel")
    public String testInstructorCancelRefundViaOutbox(
            @PathVariable UUID courseId
    ) {
        return refundFacade.reserveRefundAllPaidOrdersForCancelledCourse(courseId);
    }
}