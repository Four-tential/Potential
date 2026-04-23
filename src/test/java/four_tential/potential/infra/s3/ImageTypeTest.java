package four_tential.potential.infra.s3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageTypeTest {

    @Test
    @DisplayName("각 ImageType은 올바른 S3 prefix를 반환한다")
    void getPrefix() {
        assertThat(ImageType.PROFILE.getPrefix()).isEqualTo("profile-image");
        assertThat(ImageType.INSTRUCTOR.getPrefix()).isEqualTo("instructor-image");
        assertThat(ImageType.COURSE.getPrefix()).isEqualTo("course-image");
    }

    @Test
    @DisplayName("ImageType enum 값은 3개이다")
    void enumValues() {
        assertThat(ImageType.values()).hasSize(3);
    }
}
