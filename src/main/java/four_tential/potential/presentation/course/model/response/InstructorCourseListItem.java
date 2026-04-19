package four_tential.potential.presentation.course.model.response;

import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseStatus;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

public record InstructorCourseListItem(
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
