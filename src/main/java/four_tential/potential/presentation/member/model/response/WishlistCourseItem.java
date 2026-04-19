package four_tential.potential.presentation.member.model.response;

import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course_wishlist.WishlistCourseQueryResult;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

public record WishlistCourseItem(
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
    public static WishlistCourseItem register(WishlistCourseQueryResult result) {
        return new WishlistCourseItem(
                result.courseId(),
                result.title(),
                result.memberInstructorName(),
                result.thumbnail(),
                result.categoryCode(),
                result.categoryName(),
                result.price(),
                result.status(),
                result.startAt(),
                result.wishedAt()
        );
    }
}
