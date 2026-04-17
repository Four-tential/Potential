package four_tential.potential.presentation.instructor_member.model.response;

import four_tential.potential.domain.member.instructor_member.InstructorMemberStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record InstructorApplicationItem(
        UUID memberId,
        String memberName,
        String memberEmail,
        String categoryCode,
        String categoryName,
        InstructorMemberStatus status,
        LocalDateTime appliedAt
) {
}
