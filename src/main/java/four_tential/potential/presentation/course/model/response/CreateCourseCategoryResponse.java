package four_tential.potential.presentation.course.model.response;

import four_tential.potential.domain.course.course_category.CourseCategory;

import java.time.LocalDateTime;

public record CreateCourseCategoryResponse(
        String code,
        String name,
        LocalDateTime createdAt
) {
    public static CreateCourseCategoryResponse register(CourseCategory category) {
        return new CreateCourseCategoryResponse(
                category.getCode(),
                category.getName(),
                category.getCreatedAt()
        );
    }
}
