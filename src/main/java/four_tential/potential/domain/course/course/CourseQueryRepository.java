package four_tential.potential.domain.course.course;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CourseQueryRepository {
    Page<CourseListQueryResult> findCourses(CourseSearchCondition condition, Pageable pageable);

    Page<InstructorCourseQueryResult> findCoursesByInstructorMemberId(UUID instructorMemberId, Pageable pageable);
}
