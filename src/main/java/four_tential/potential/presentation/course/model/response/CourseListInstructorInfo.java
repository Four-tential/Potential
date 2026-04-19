package four_tential.potential.presentation.course.model.response;

import java.util.UUID;

public record CourseListInstructorInfo(
        UUID memberId,
        String name,
        String profileImageUrl
) {
}
