package four_tential.potential.presentation.instructor_member.model.response;

import four_tential.potential.domain.member.instructor_member.InstructorApplicationDetailResult;
import four_tential.potential.domain.member.instructor_member.InstructorMemberStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record InstructorApplicationDetail(
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
    public static InstructorApplicationDetail register(InstructorApplicationDetailResult result) {
        return new InstructorApplicationDetail(
                result.memberId(),
                result.memberName(),
                result.memberEmail(),
                result.memberPhone(),
                result.categoryCode(),
                result.categoryName(),
                result.content(),
                result.imageUrl(),
                result.status(),
                result.rejectReason(),
                result.appliedAt(),
                result.respondedAt()
        );
    }
}
