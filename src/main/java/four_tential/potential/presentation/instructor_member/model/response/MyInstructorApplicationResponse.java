package four_tential.potential.presentation.instructor_member.model.response;

import four_tential.potential.domain.member.instructor_member.InstructorMemberStatus;

import java.time.LocalDateTime;

public record MyInstructorApplicationResponse(
        String categoryCode,
        String categoryName,
        String content,
        String imageUrl,
        InstructorMemberStatus status,
        String rejectReason,
        LocalDateTime appliedAt,
        LocalDateTime respondedAt
) {}
