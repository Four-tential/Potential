package four_tential.potential.domain.member.instructor_member;

import java.time.LocalDateTime;

public record MyInstructorApplicationResult(
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
