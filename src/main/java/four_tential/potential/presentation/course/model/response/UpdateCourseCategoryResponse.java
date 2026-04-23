package four_tential.potential.presentation.course.model.response;

import four_tential.potential.domain.course.course_category.CourseCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record UpdateCourseCategoryResponse(
        @Schema(example = "YOGA") String code,
        @Schema(example = "하타 요가") String name,
        @Schema(example = "2025-06-01T12:00:00") LocalDateTime updatedAt
) {
    public static UpdateCourseCategoryResponse register(CourseCategory category) {
        return new UpdateCourseCategoryResponse(
                category.getCode(),
                category.getName(),
                category.getUpdatedAt()
        );
    }
}
