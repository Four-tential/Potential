package four_tential.potential.domain.course.fixture;

import four_tential.potential.domain.course.course_image.CourseImage;

import java.util.UUID;

public class CourseImageFixture {

    public static final UUID DEFAULT_COURSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    public static final String DEFAULT_IMAGE_URL = "https://cdn.example.com/images/course/sample.jpg";

    public static CourseImage defaultCourseImage() {
        return CourseImage.register(DEFAULT_COURSE_ID, DEFAULT_IMAGE_URL);
    }
}
