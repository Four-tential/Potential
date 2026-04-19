package four_tential.potential.domain.course.course;

import four_tential.potential.presentation.course.model.request.CourseSearchRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CourseQueryRepository {
    Page<CourseListQueryResult> findCourses(CourseSearchRequest condition, Pageable pageable);
}
