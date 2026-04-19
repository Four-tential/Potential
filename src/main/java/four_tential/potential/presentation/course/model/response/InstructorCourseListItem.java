package four_tential.potential.presentation.course.model.response;

import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course.InstructorCourseQueryResult;

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
    public static InstructorCourseListItem register(InstructorCourseQueryResult result) {
        return new InstructorCourseListItem(
                result.courseId(),
                result.title(),
                result.level(),
                result.status(),
                result.capacity(),
                result.confirmCount(),
                result.price(),
                result.orderOpenAt(),
                result.startAt()
        );
    }
}
