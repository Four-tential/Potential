package four_tential.potential.presentation.member.model.response;

import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course_wishlist.WishlistCourseQueryResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

public record WishlistCourseItem(
        @Schema(example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890") UUID courseId,
        @Schema(example = "초급자를 위한 하타 요가") String title,
        @Schema(example = "김강사") String memberInstructorName,
        @Schema(example = "https://cdn.example.com/course-image/a1b2c3d4/thumbnail.jpg") String thumbnail,
        @Schema(example = "YOGA") String categoryCode,
        @Schema(example = "요가") String categoryName,
        @Schema(example = "120000") BigInteger price,
        @Schema(example = "OPEN") CourseStatus status,
        @Schema(example = "2025-06-01T10:00:00") LocalDateTime startAt,
        @Schema(example = "2025-05-20T14:30:00") LocalDateTime wishedAt
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
