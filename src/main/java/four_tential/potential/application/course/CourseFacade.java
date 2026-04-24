package four_tential.potential.application.course;

import four_tential.potential.domain.course.course.Course;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CourseFacade {

    private final CourseService courseService;

    /**
     * 코스 엔티티 조회 (내부용)
     */
    public Course getCourseEntity(UUID courseId) {
        return courseService.getCourseEntity(courseId);
    }
}
