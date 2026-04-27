package four_tential.potential.domain.course.course_image;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CourseImageRepository extends JpaRepository<CourseImage, UUID> {

    @Query("SELECT ci.imageUrl FROM CourseImage ci WHERE ci.course.id = :courseId ORDER BY ci.id ASC")
    List<String> findImageUrlsByCourseId(@Param("courseId") UUID courseId);
}
