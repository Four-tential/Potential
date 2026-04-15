package four_tential.potential.domain.course.fixture;

import four_tential.potential.domain.course.course_category.CourseCategory;

public class CourseCategoryFixture {

    public static final String DEFAULT_CODE = "BACKEND";
    public static final String DEFAULT_NAME = "백엔드";

    public static CourseCategory defaultCourseCategory() {
        return CourseCategory.register(DEFAULT_CODE, DEFAULT_NAME);
    }

    public static CourseCategory courseCategoryWithCode(String code) {
        return CourseCategory.register(code, DEFAULT_NAME);
    }
}
