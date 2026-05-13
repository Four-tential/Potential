package four_tential.potential.presentation.instructor_member.model.response;

import four_tential.potential.domain.member.instructor_member.InstructorMember;
import four_tential.potential.domain.member.instructor_member.InstructorMemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ApplyInstructorResponse(
        @Schema(example = "PENDING") InstructorMemberStatus status,
        @Schema(example = "YOGA") String categoryCode,
        @Schema(example = "2025-06-01T10:00:00") LocalDateTime appliedAt
) {
    public static ApplyInstructorResponse register(InstructorMember instructorMember) {
        return new ApplyInstructorResponse(
                instructorMember.getStatus(),
                instructorMember.getCategoryCode(),
                instructorMember.getUpdatedAt()
        );
    }
}
