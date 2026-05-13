package four_tential.potential.presentation.course.model.response;

import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course_category.CourseCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CourseEntityResponseDtoTest {

    private Course createCourse() {
        return Course.register(
                UUID.randomUUID(), UUID.randomUUID(),
                "초급 하타 요가", "기초부터 배우는 요가",
                "서울시 강남구", "2층",
                20, BigInteger.valueOf(120000), CourseLevel.BEGINNER,
                LocalDateTime.of(2025, 5, 1, 10, 0),
                LocalDateTime.of(2025, 5, 20, 23, 59),
                LocalDateTime.of(2025, 6, 1, 10, 0),
                LocalDateTime.of(2025, 6, 30, 18, 0)
        );
    }

    @Test
    @DisplayName("CreateCourseRequestResponse.register는 코스 생성 결과를 올바르게 매핑한다")
    void createCourseRequestResponse_register() {
        Course course = createCourse();

        CreateCourseRequestResponse response = CreateCourseRequestResponse.register(course, "YOGA");

        assertThat(response.title()).isEqualTo("초급 하타 요가");
        assertThat(response.categoryCode()).isEqualTo("YOGA");
        assertThat(response.status()).isEqualTo(CourseStatus.PREPARATION);
    }

    @Test
    @DisplayName("CourseRequestActionResponse.from은 코스 상태를 올바르게 매핑한다")
    void courseRequestActionResponse_from() {
        Course course = createCourse();

        CourseRequestActionResponse response = CourseRequestActionResponse.from(course);

        assertThat(response.status()).isEqualTo(CourseStatus.PREPARATION);
    }

    @Test
    @DisplayName("UpdateCourseResponse.from은 코스 수정 결과를 올바르게 매핑한다")
    void updateCourseResponse_from() {
        Course course = createCourse();

        UpdateCourseResponse response = UpdateCourseResponse.from(course);

        assertThat(response.title()).isEqualTo("초급 하타 요가");
    }

    @Test
    @DisplayName("CreateCourseCategoryResponse.register는 카테고리 생성 결과를 올바르게 매핑한다")
    void createCourseCategoryResponse_register() {
        CourseCategory category = CourseCategory.register("YOGA", "요가");

        CreateCourseCategoryResponse response = CreateCourseCategoryResponse.register(category);

        assertThat(response.code()).isEqualTo("YOGA");
        assertThat(response.name()).isEqualTo("요가");
    }

    @Test
    @DisplayName("UpdateCourseCategoryResponse.register는 카테고리 수정 결과를 올바르게 매핑한다")
    void updateCourseCategoryResponse_register() {
        CourseCategory category = CourseCategory.register("YOGA", "요가");

        UpdateCourseCategoryResponse response = UpdateCourseCategoryResponse.register(category);

        assertThat(response.code()).isEqualTo("YOGA");
        assertThat(response.name()).isEqualTo("요가");
    }
}
