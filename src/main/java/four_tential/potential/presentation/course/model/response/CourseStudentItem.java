package four_tential.potential.presentation.course.model.response;

import four_tential.potential.domain.attendance.AttendanceStatus;
import four_tential.potential.domain.order.CourseStudentQueryResult;

import java.time.LocalDateTime;
import java.util.UUID;

public record CourseStudentItem(
        UUID memberId,
        String memberName,
        AttendanceStatus attendanceStatus,
        LocalDateTime attendanceAt
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
