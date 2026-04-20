package four_tential.potential.presentation.course.model.request;

import four_tential.potential.domain.course.course_approval_history.CourseApprovalAction;
import jakarta.validation.constraints.NotNull;

public record CourseRequestActionRequest(
        @NotNull(message = "action을 입력해주세요")
        CourseApprovalAction action,
        String rejectReason
) {
}
