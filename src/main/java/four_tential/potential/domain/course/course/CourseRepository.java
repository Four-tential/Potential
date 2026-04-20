package four_tential.potential.domain.course.course;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID>, CourseQueryRepository {
    boolean existsByMemberInstructorIdAndStatusInAndEndAtAfter(
            UUID memberInstructorId,
            Collection<CourseStatus> statuses,
            LocalDateTime now
    );

    long countByMemberInstructorId(UUID memberInstructorId);

    boolean existsByCourseCategoryId(UUID courseCategoryId);
}
