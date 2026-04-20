package four_tential.potential.domain.order;

import four_tential.potential.domain.attendance.AttendanceStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record CourseStudentQueryResult(
        UUID memberId,
        String memberName,
        AttendanceStatus attendanceStatus,
        LocalDateTime attendanceAt
) {}
