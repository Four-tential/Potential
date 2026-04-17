package four_tential.potential.presentation.instructor_member.model.response;

import four_tential.potential.domain.member.instructor_member.InstructorMember;
import four_tential.potential.domain.member.instructor_member.InstructorMemberStatus;

import java.time.LocalDateTime;

public record ApplyInstructorResponse(
        InstructorMemberStatus status,
        String categoryCode,
        LocalDateTime appliedAt
) {
    public static ApplyInstructorResponse register(InstructorMember instructorMember) {
        return new ApplyInstructorResponse(
                instructorMember.getStatus(),
                instructorMember.getCategoryCode(),
                instructorMember.getUpdatedAt()
        );
    }
}
