package four_tential.potential.presentation.member.model.response;

import four_tential.potential.domain.course.course.CourseStatus;

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
    public static WishlistCourseItem register(
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
        return new WishlistCourseItem(
                courseId,
                title,
                memberInstructorName,
                thumbnail,
                categoryCode,
                categoryName,
                price,
                status,
                startAt,
                wishedAt
        );
    }
}
