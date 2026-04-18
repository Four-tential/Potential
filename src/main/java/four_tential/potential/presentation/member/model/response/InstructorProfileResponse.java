package four_tential.potential.presentation.member.model.response;

import java.util.UUID;

public record InstructorProfileResponse(
        UUID memberId,
        String name,
        String profileImageUrl,
        String categoryCode,
        String categoryName,
        String content,
        long courseCount,
        double averageRating,
        long totalStudentCount
) {
}
