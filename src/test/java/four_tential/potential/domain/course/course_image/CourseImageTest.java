package four_tential.potential.domain.course.course_image;

import four_tential.potential.domain.course.fixture.CourseImageFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CourseImageTest {

    @Test
    @DisplayName("register() 성공 시 courseId와 imageUrl이 설정됨")
    void register() {
        CourseImage image = CourseImageFixture.defaultCourseImage();

        assertThat(image.getCourseId()).isEqualTo(CourseImageFixture.DEFAULT_COURSE_ID);
        assertThat(image.getImageUrl()).isEqualTo(CourseImageFixture.DEFAULT_IMAGE_URL);
    }

    @Test
    @DisplayName("생성된 코스 이미지는 id가 null")
    void registerInitialState() {
        CourseImage image = CourseImageFixture.defaultCourseImage();

        assertThat(image.getId()).isNull();
    }
}
