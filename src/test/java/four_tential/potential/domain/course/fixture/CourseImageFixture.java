package four_tential.potential.domain.course.fixture;

import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course_image.CourseImage;

public class CourseImageFixture {

    public static final String DEFAULT_IMAGE_URL = "https://cdn.example.com/images/course/sample.jpg";

    public static CourseImage defaultCourseImage() {
        Course course = CourseFixture.defaultCourse();
        return CourseImage.register(course, DEFAULT_IMAGE_URL);
    }
}
