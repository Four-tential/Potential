package four_tential.potential.presentation.course.model.response;

import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record CourseRequestActionResponse(
        UUID courseId,
        CourseStatus status,
        LocalDateTime confirmedAt
) {
    public static CourseRequestActionResponse from(Course course) {
        return new CourseRequestActionResponse(course.getId(), course.getStatus(), course.getConfirmedAt());
    }
}
