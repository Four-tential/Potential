package four_tential.potential.presentation.course.model.response;

import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseStatus;

import java.util.UUID;

public record CreateCourseRequestResponse(
        UUID courseId,
        String title,
        String categoryCode,
        CourseStatus status
) {
    public static CreateCourseRequestResponse register(Course course, String categoryCode) {
        return new CreateCourseRequestResponse(
                course.getId(),
                course.getTitle(),
                categoryCode,
                course.getStatus()
        );
    }
}
