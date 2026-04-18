package four_tential.potential.domain.course.course_wishlist;

import four_tential.potential.presentation.member.model.response.WishlistCourseItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CourseWishlistQueryRepository {
    Page<WishlistCourseItem> findWishlistCourses(UUID memberId, Pageable pageable);
}
