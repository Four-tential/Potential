package four_tential.potential.presentation.course.model.response;

import four_tential.potential.domain.course.course.Course;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

public record UpdateCourseResponse(
        @Schema(example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890") UUID courseId,
        @Schema(example = "초급자를 위한 하타 요가 (수정)") String title,
        @Schema(example = "2025-06-01T15:00:00") LocalDateTime updatedAt
) {

    public static UpdateCourseResponse from(Course course) {
        return new UpdateCourseResponse(course.getId(), course.getTitle(), course.getUpdatedAt());
    }
}
