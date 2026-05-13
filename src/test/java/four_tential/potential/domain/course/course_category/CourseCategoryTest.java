package four_tential.potential.domain.course.course_category;

import four_tential.potential.domain.course.fixture.CourseCategoryFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CourseCategoryTest {

    @Test
    @DisplayName("register() 성공 시 code와 name이 설정됨")
    void register() {
        CourseCategory category = CourseCategoryFixture.defaultCourseCategory();

        assertThat(category.getCode()).isEqualTo(CourseCategoryFixture.DEFAULT_CODE);
        assertThat(category.getName()).isEqualTo(CourseCategoryFixture.DEFAULT_NAME);
    }

    @Test
    @DisplayName("생성된 카테고리는 id가 null")
    void registerInitialState() {
        CourseCategory category = CourseCategoryFixture.defaultCourseCategory();

        assertThat(category.getId()).isNull();
    }

    @Test
    @DisplayName("courseCategoryWithCode()로 생성 시 지정한 code가 설정됨")
    void courseCategoryWithCode() {
        CourseCategory category = CourseCategoryFixture.courseCategoryWithCode("FRONTEND");

        assertThat(category.getCode()).isEqualTo("FRONTEND");
        assertThat(category.getName()).isEqualTo(CourseCategoryFixture.DEFAULT_NAME);
    }

    @Test
    @DisplayName("updateName() 호출 시 name이 변경됨")
    void updateName() {
        CourseCategory category = CourseCategoryFixture.defaultCourseCategory();

        category.updateName("새 카테고리 이름");

        assertThat(category.getName()).isEqualTo("새 카테고리 이름");
        assertThat(category.getCode()).isEqualTo(CourseCategoryFixture.DEFAULT_CODE);
    }
}
