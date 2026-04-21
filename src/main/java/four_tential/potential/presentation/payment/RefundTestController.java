package four_tential.potential.presentation.payment;

import four_tential.potential.application.payment.RefundFacade;
import four_tential.potential.presentation.payment.dto.RefundCourseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 강사 코스 취소 일괄 환불 임시 테스트용 컨트롤러
 */
@RestController
@RequestMapping("/test/refund")
@RequiredArgsConstructor
public class RefundTestController {

    private final RefundFacade refundFacade;

    @PostMapping("/courses/{courseId}/instructor-cancel")
    public RefundCourseResponse testInstructorCancelRefund(
            @PathVariable UUID courseId
    ) {
        return refundFacade.refundAllPaidOrdersForCancelledCourse(courseId);
    }
}