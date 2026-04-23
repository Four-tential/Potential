package four_tential.potential.presentation.course.model.response;

import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseListQueryResult;
import four_tential.potential.domain.course.course.CourseStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

public record CourseListItem(
        @Schema(example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890") UUID courseId,
        @Schema(example = "초급자를 위한 하타 요가") String title,
        @Schema(example = "YOGA") String categoryCode,
        @Schema(example = "요가") String categoryName,
        CourseListInstructorInfo instructor,
        @Schema(example = "https://cdn.example.com/course-image/a1b2c3d4/thumbnail.jpg") String thumbnailUrl,
        @Schema(example = "120000") BigInteger price,
        @Schema(example = "20") int capacity,
        @Schema(example = "15") int confirmCount,
        @Schema(example = "OPEN") CourseStatus status,
        @Schema(example = "BEGINNER") CourseLevel level,
        @Schema(example = "2025-05-01T10:00:00") LocalDateTime orderOpenAt,
        @Schema(example = "2025-06-01T10:00:00") LocalDateTime startAt,
        @Schema(example = "false") boolean isWishlisted
) {
    public static CourseListItem register(CourseListQueryResult result, boolean isWishlisted) {
        return new CourseListItem(
                result.courseId(),
                result.title(),
                result.categoryCode(),
                result.categoryName(),
                new CourseListInstructorInfo(
                        result.instructorMemberId(),
                        result.instructorName(),
                        result.instructorProfileImageUrl()
                ),
                result.thumbnailUrl(),
                result.price(),
                result.capacity(),
                result.confirmCount(),
                result.status(),
                result.level(),
                result.orderOpenAt(),
                result.startAt(),
                isWishlisted
        );
    }
}
