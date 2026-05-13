package four_tential.potential.domain.member.instructor_member;

import java.time.LocalDateTime;
import java.util.UUID;

public record InstructorApplicationItemResult(
        UUID memberId,
        String memberName,
        String memberEmail,
        String categoryCode,
        String categoryName,
        InstructorMemberStatus status,
        LocalDateTime appliedAt
) {
}
