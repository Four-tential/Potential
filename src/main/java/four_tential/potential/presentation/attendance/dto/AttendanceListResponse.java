package four_tential.potential.presentation.attendance.dto;

import four_tential.potential.domain.attendance.Attendance;
import four_tential.potential.domain.attendance.AttendanceStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AttendanceListResponse(
        Integer totalCount,
        Integer attendCount,
        Integer absentCount,
        List<AttendanceDetail> attendances
) {
    public record AttendanceDetail(
            UUID attendanceId,
            UUID memberId,
            AttendanceStatus status,
            LocalDateTime attendanceAt
    ) {}

    // 강사용 팩토리 메서드
    public static AttendanceListResponse ofInstructor(List<Attendance> attendances) {
        List<AttendanceDetail> details = attendances.stream()
                .map(a -> new AttendanceDetail(
                        a.getId(),
                        a.getMemberId(),
                        a.getStatus(),
                        a.getAttendanceAt()
                ))
                .toList();

        long attendCount = attendances.stream()
                .filter(a -> a.getStatus() == AttendanceStatus.ATTEND)
                .count();

        return new AttendanceListResponse(
                attendances.size(),
                (int) attendCount,
                attendances.size() - (int) attendCount,
                details
        );
    }

    // 수강생용 팩토리 메서드
    public static AttendanceListResponse ofStudent(Attendance attendance) {
        return new AttendanceListResponse(
                null,
                null,
                null,
                List.of(new AttendanceDetail(
                        attendance.getId(),
                        attendance.getMemberId(),
                        attendance.getStatus(),
                        attendance.getAttendanceAt()
                ))
        );
    }
}