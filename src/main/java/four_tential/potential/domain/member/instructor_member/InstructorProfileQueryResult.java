package four_tential.potential.domain.member.instructor_member;

import java.util.UUID;

public record InstructorProfileQueryResult(
        UUID memberId,
        String memberName,
        String instructorImageUrl,
        String categoryCode,
        String categoryName,
        String content,
        long courseCount,
        double averageRating,
        long totalStudentCount
) {
}
