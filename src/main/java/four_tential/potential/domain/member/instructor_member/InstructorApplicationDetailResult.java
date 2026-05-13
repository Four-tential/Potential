package four_tential.potential.domain.member.instructor_member;

import java.time.LocalDateTime;
import java.util.UUID;

public record InstructorApplicationDetailResult(
        UUID memberId,
        String memberName,
        String memberEmail,
        String memberPhone,
        String categoryCode,
        String categoryName,
        String content,
        String imageUrl,
        InstructorMemberStatus status,
        String rejectReason,
        LocalDateTime appliedAt,
        LocalDateTime respondedAt
) {
}
