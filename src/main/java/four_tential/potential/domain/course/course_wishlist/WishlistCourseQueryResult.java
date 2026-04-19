package four_tential.potential.domain.course.course_wishlist;

import four_tential.potential.domain.course.course.CourseStatus;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

public record WishlistCourseQueryResult(
        UUID courseId,
        String title,
        String memberInstructorName,
        String thumbnail,
        String categoryCode,
        String categoryName,
        BigInteger price,
        CourseStatus status,
        LocalDateTime startAt,
        LocalDateTime wishedAt
) {
}
