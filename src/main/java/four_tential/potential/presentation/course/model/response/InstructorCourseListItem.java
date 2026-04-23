package four_tential.potential.presentation.course.model.response;

import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course.InstructorCourseQueryResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

public record InstructorCourseListItem(
        @Schema(example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890") UUID courseId,
        @Schema(example = "초급자를 위한 하타 요가") String title,
        @Schema(example = "BEGINNER") CourseLevel level,
        @Schema(example = "OPEN") CourseStatus status,
        @Schema(example = "20") int capacity,
        @Schema(example = "15") int confirmCount,
        @Schema(example = "120000") BigInteger price,
        @Schema(example = "2025-05-01T10:00:00") LocalDateTime orderOpenAt,
        @Schema(example = "2025-06-01T10:00:00") LocalDateTime startAt
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
