package four_tential.potential.presentation.course.model.response;

import four_tential.potential.domain.course.course_category.CourseCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record CreateCourseCategoryResponse(
        @Schema(example = "YOGA") String code,
        @Schema(example = "요가") String name,
        @Schema(example = "2025-01-01T00:00:00") LocalDateTime createdAt
) {
    public static CreateCourseCategoryResponse register(CourseCategory category) {
        return new CreateCourseCategoryResponse(
                category.getCode(),
                category.getName(),
                category.getCreatedAt()
        );
    }
}
