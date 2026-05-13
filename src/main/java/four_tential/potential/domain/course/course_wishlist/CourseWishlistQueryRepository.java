package four_tential.potential.domain.course.course_wishlist;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CourseWishlistQueryRepository {
    Page<WishlistCourseQueryResult> findWishlistCourses(UUID memberId, Pageable pageable);
}
