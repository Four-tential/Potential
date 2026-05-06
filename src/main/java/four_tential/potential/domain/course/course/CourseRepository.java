package four_tential.potential.domain.course.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID>, CourseQueryRepository {
    boolean existsByMemberInstructorIdAndStatusInAndEndAtAfter(
            UUID memberInstructorId,
            Collection<CourseStatus> statuses,
            LocalDateTime now
    );

    long countByMemberInstructorId(UUID memberInstructorId);

    boolean existsByCourseCategoryId(UUID courseCategoryId);

    List<Course> findAllByTitleStartingWith(String prefix);

    @Modifying
    @Query("delete from Course c where c.title like concat(:prefix, '%')")
    void deleteByTitleStartingWith(@Param("prefix") String prefix);
}
