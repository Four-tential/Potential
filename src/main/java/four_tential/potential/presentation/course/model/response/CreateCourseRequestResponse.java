package four_tential.potential.presentation.course.model.response;

import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record CreateCourseRequestResponse(
        @Schema(example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890") UUID courseId,
        @Schema(example = "초급자를 위한 하타 요가") String title,
        @Schema(example = "YOGA") String categoryCode,
        @Schema(example = "PREPARATION") CourseStatus status
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
