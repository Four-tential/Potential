package four_tential.potential.presentation.payment;

import four_tential.potential.application.payment.RefundFacade;
import four_tential.potential.presentation.payment.dto.RefundCourseResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundTestControllerTest {

    @InjectMocks
    private RefundTestController refundTestController;

    @Mock
    private RefundFacade refundFacade;

    @Test
    @DisplayName("즉시 일괄 환불 테스트 엔드포인트는 facade의 즉시 환불을 호출한다")
    void testInstructorCancelRefund_calls_immediate_refund() {
        UUID courseId = UUID.randomUUID();
        RefundCourseResponse response = RefundCourseResponse.of(courseId, "Test Course", 3, 2, 1, 50000L);
        given(refundFacade.refundAllPaidOrdersForCancelledCourse(courseId)).willReturn(response);

        RefundCourseResponse result = refundTestController.testInstructorCancelRefund(courseId);

        assertThat(result).isEqualTo(response);
        verify(refundFacade).refundAllPaidOrdersForCancelledCourse(courseId);
    }

    @Test
    @DisplayName("배치 예약 테스트 엔드포인트는 facade의 예약 메서드를 호출한다")
    void testInstructorCancelRefundViaOutbox_calls_reserve_refund() {
        UUID courseId = UUID.randomUUID();
        given(refundFacade.reserveRefundAllPaidOrdersForCancelledCourse(courseId))
                .willReturn("환불 예약 완료. Batch가 5분 내로 처리합니다. courseId=" + courseId);

        String result = refundTestController.testInstructorCancelRefundViaOutbox(courseId);

        assertThat(result).contains("환불 예약 완료");
        verify(refundFacade).reserveRefundAllPaidOrdersForCancelledCourse(courseId);
    }
}
