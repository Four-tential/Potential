package four_tential.potential.presentation.course.model.response;

import four_tential.potential.domain.course.course.CourseListQueryResult;
import four_tential.potential.domain.course.course.CourseStatus;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

public record CourseListItem(
        UUID courseId,
        String title,
        String categoryCode,
        String categoryName,
        CourseListInstructorInfo instructor,
        String thumbnailUrl,
        BigInteger price,
        int capacity,
        int confirmCount,
        CourseStatus status,
        LocalDateTime orderOpenAt,
        LocalDateTime startAt,
        boolean isWishlisted
) {
    public static CourseListItem from(CourseListQueryResult result, boolean isWishlisted) {
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
                result.orderOpenAt(),
                result.startAt(),
                isWishlisted
        );
    }
}
