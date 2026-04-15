package four_tential.potential.domain.course.course_wishlist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourseWishlistRepository extends JpaRepository<CourseWishlist, UUID> {
}
