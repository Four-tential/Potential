package four_tential.potential.domain.course.course_category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface CourseCategoryRepository extends JpaRepository<CourseCategory, UUID> {
    boolean existsByCode(String code);

    @Query("SELECT c.code FROM CourseCategory c WHERE c.code IN :codes")
    Set<String> findExistingCodes(@Param("codes") Collection<String> codes);
}
