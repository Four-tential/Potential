package four_tential.potential.domain.course.course_category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourseCategoryRepository extends JpaRepository<CourseCategory, UUID> {
    boolean existsByCode(String code);
}
