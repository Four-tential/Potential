package four_tential.potential.presentation.attendance.dto;

import four_tential.potential.domain.attendance.Attendance;
import four_tential.potential.domain.attendance.AttendanceStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class AttendanceEventResponse {

    private UUID memberId;
    private AttendanceStatus status;
    private LocalDateTime attendanceAt;

    public static AttendanceEventResponse from(Attendance attendance) {
        return AttendanceEventResponse.builder()
                .memberId(attendance.getMemberId())
                .status(attendance.getStatus())
                .attendanceAt(attendance.getAttendanceAt())
                .build();
    }
}