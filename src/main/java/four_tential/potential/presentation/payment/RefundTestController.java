package four_tential.potential.presentation.payment;

import four_tential.potential.application.payment.RefundFacade;
import four_tential.potential.presentation.payment.dto.RefundCourseResponse;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 강사 코스 취소 일괄 환불 테스트용 컨트롤러
 */
@Tag(name = "환불 테스트", description = "강사 코스 취소 일괄 환불 검증용 테스트 API")
@RestController
@RequestMapping("/v1/refund")
@RequiredArgsConstructor
public class RefundTestController {

    private final RefundFacade refundFacade;

    // MVP 방식
    @Hidden
    @PostMapping("/courses/{courseId}/instructor-cancel/immediate")
    public RefundCourseResponse testInstructorCancelRefund(
            @PathVariable UUID courseId
    ) {
        return refundFacade.refundAllPaidOrdersForCancelledCourse(courseId);
    }

    // 고도화 방식. Outbox에 환불 예약만 저장
    @Operation(
            summary = "강사 코스 취소 일괄 환불 예약",
            description = """
                    강사 코스 취소 시 일괄 환불을 즉시 실행하지 않고 Outbox에 먼저 예약합니다.

                    - 대상 주문을 REFUND_PENDING 상태로 전환합니다.
                    - course_cancel_outbox에 PENDING 이벤트를 저장합니다.
                    - 이후 Scheduler + Batch가 refund_task 생성과 실제 환불 실행을 이어서 처리합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "일괄 환불 예약 성공"),
            @ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "예약 가능한 환불 대상 주문이 없음")
    })
    @PostMapping("/courses/{courseId}/instructor-cancel")
    public String testInstructorCancelRefundViaOutbox(
            @Parameter(description = "일괄 환불을 예약할 코스 ID", required = true)
            @PathVariable UUID courseId
    ) {
        return refundFacade.reserveRefundAllPaidOrdersForCancelledCourse(courseId);
    }
}
