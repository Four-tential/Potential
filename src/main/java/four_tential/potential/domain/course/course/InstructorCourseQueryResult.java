package four_tential.potential.domain.course.course;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

public record InstructorCourseQueryResult(
        UUID courseId,
        String title,
        CourseLevel level,
        CourseStatus status,
        int capacity,
        int confirmCount,
        BigInteger price,
        LocalDateTime orderOpenAt,
        LocalDateTime startAt
) {
}
