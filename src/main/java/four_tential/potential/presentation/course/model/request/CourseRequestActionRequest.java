package four_tential.potential.presentation.course.model.request;

import four_tential.potential.domain.course.course_approval_history.CourseApprovalAction;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record CourseRequestActionRequest(
        @NotNull(message = "action을 입력해주세요")
        CourseApprovalAction action,
        String rejectReason
) {
    @AssertTrue(message = "반려 사유는 필수입니다")
    public boolean isValidRejectReason() {
        return action != CourseApprovalAction.REJECT
                || (rejectReason != null && !rejectReason.isBlank());
    }
}
