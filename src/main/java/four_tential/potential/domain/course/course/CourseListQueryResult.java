package four_tential.potential.domain.course.course;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

public record CourseListQueryResult(
        UUID courseId,
        String title,
        String categoryCode,
        String categoryName,
        UUID instructorMemberId,
        String instructorName,
        String instructorProfileImageUrl,
        String thumbnailUrl,
        BigInteger price,
        int capacity,
        int confirmCount,
        CourseStatus status,
        LocalDateTime orderOpenAt,
        LocalDateTime startAt
) {
}
