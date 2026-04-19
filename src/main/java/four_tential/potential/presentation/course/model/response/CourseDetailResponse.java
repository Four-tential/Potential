package four_tential.potential.presentation.course.model.response;

import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseStatus;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CourseDetailResponse(
        UUID courseId,
        String title,
        String description,
        String categoryCode,
        String categoryName,
        CourseDetailInstructorInfo instructor,
        List<String> images,
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
        double averageRating,
        long reviewCount,
        boolean isWishlisted
) {
}
