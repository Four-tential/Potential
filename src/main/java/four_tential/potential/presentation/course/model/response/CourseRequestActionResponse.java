package four_tential.potential.presentation.course.model.response;

import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

public record CourseRequestActionResponse(
        @Schema(example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890") UUID courseId,
        @Schema(example = "OPEN") CourseStatus status,
        @Schema(example = "2025-06-01T09:00:00") LocalDateTime confirmedAt
) {
    public static CourseRequestActionResponse from(Course course) {
        return new CourseRequestActionResponse(course.getId(), course.getStatus(), course.getConfirmedAt());
    }
}
