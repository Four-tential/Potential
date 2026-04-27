package four_tential.potential.domain.course.course;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

public record CourseDetailQueryResult(
        UUID courseId,
        String title,
        String description,
        String categoryCode,
        String categoryName,
        UUID instructorMemberId,
        String instructorName,
        String instructorProfileImageUrl,
        String addressMain,
        String addressDetail,
        BigInteger price,
        int capacity,
        int confirmCount,
        CourseStatus status,
        CourseLevel level,
        LocalDateTime orderOpenAt,
        LocalDateTime orderCloseAt,
        LocalDateTime startAt,
        LocalDateTime endAt,
        double instructorAvgRating,
        double courseAvgRating,
        long reviewCount
) {
}
