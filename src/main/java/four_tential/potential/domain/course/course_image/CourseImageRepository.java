package four_tential.potential.domain.course.course_image;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourseImageRepository extends JpaRepository<CourseImage, UUID> {
}
