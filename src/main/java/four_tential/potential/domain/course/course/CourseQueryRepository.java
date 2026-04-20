package four_tential.potential.domain.course.course;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CourseQueryRepository {
    Page<CourseListQueryResult> findCourses(CourseSearchCondition condition, Pageable pageable);

    // 외부 검색용은 PREPARATION 제외
    Page<InstructorCourseQueryResult> findCoursesByInstructorMemberId(UUID instructorMemberId, Pageable pageable);

    // 강사 본인용은 PREPARATION 포함 전체 상태
    Page<InstructorCourseQueryResult> findMyCoursesByInstructorMemberId(UUID instructorMemberId, Pageable pageable);
}
