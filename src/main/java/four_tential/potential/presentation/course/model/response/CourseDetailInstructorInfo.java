package four_tential.potential.presentation.course.model.response;

import java.util.UUID;

public record CourseDetailInstructorInfo(
        UUID memberId,
        String name,
        String profileImageUrl,
        double averageRating
) {
}
