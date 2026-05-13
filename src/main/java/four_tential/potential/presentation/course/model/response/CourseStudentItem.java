package four_tential.potential.presentation.course.model.response;

import four_tential.potential.domain.attendance.AttendanceStatus;
import four_tential.potential.domain.order.CourseStudentQueryResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

public record CourseStudentItem(
        @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") UUID memberId,
        @Schema(example = "홍길동") String memberName,
        @Schema(example = "ATTENDED") AttendanceStatus attendanceStatus,
        @Schema(example = "2025-06-01T10:05:00") LocalDateTime attendanceAt
) {
    public static CourseStudentItem register(CourseStudentQueryResult result) {
        return new CourseStudentItem(
                result.memberId(),
                result.memberName(),
                result.attendanceStatus(),
                result.attendanceAt()
        );
    }
}
