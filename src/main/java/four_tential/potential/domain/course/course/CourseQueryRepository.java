package four_tential.potential.domain.course.course;

import four_tential.potential.presentation.course.model.request.CourseSearchRequest;
import four_tential.potential.presentation.course.model.response.InstructorCourseListItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CourseQueryRepository {
    Page<CourseListQueryResult> findCourses(CourseSearchRequest condition, Pageable pageable);

    Page<InstructorCourseListItem> findCoursesByInstructorMemberId(UUID instructorMemberId, Pageable pageable);
}
