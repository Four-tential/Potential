package four_tential.potential.presentation.instructor_member.model.request;

import jakarta.validation.constraints.NotNull;

public record InstructorActionRequest(
        @NotNull(message = "처리 유형을 입력해주세요")
        InstructorAction action,

        String rejectReason
) {
}
