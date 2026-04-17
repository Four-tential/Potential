package four_tential.potential.presentation.instructor_member.model.response;

import four_tential.potential.domain.member.instructor_member.InstructorMember;
import four_tential.potential.domain.member.instructor_member.InstructorMemberStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record InstructorActionResponse(
        UUID memberId,
        InstructorMemberStatus status,
        LocalDateTime respondedAt
) {
    public static InstructorActionResponse register(InstructorMember instructorMember) {
        return new InstructorActionResponse(
                instructorMember.getMemberId(),
                instructorMember.getStatus(),
                instructorMember.getRespondedAt()
        );
    }
}
