package four_tential.potential.presentation.course.model.response;

import four_tential.potential.domain.course.course_category.CourseCategory;

import java.time.LocalDateTime;

public record UpdateCourseCategoryResponse(
        String code,
        String name,
        LocalDateTime updatedAt
) {
    public static UpdateCourseCategoryResponse register(CourseCategory category) {
        return new UpdateCourseCategoryResponse(
                category.getCode(),
                category.getName(),
                category.getUpdatedAt()
        );
    }
}
