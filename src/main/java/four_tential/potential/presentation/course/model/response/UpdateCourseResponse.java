package four_tential.potential.presentation.course.model.response;

import four_tential.potential.domain.course.course.Course;

import java.time.LocalDateTime;
import java.util.UUID;

public record UpdateCourseResponse(UUID courseId, String title, LocalDateTime updatedAt) {

    public static UpdateCourseResponse from(Course course) {
        return new UpdateCourseResponse(course.getId(), course.getTitle(), course.getUpdatedAt());
    }
}
