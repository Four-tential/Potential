package four_tential.potential.domain.attendance.dto;

import four_tential.potential.domain.attendance.Attendance;
import four_tential.potential.domain.attendance.AttendanceStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AttendanceResponse {

    // 강사용 집계 (수강생 조회 시 null)
    private Integer totalCount;
    private Integer attendCount;
    private Integer absentCount;

    // 출석 목록 (강사/전체, 수강생/본인 단건을 리스트로)
    private List<AttendanceDetail> attendances;

    // 단건 상세
    @Getter
    @Builder
    public static class AttendanceDetail {
        private UUID attendanceId;
        private UUID memberId;
        private AttendanceStatus status;
        private LocalDateTime attendanceAt;
    }

    // 강사용 팩토리 메서드
    public static AttendanceResponse ofInstructor(List<Attendance> attendances) {
        List<AttendanceDetail> details = attendances.stream()
                .map(a -> AttendanceDetail.builder()
                        .attendanceId(a.getId())
                        .memberId(a.getMemberId())
                        .status(a.getStatus())
                        .attendanceAt(a.getAttendanceAt())
                        .build())
                .toList();

        long attendCount = attendances.stream()
                .filter(a -> a.getStatus() == AttendanceStatus.ATTEND)
                .count();

        return AttendanceResponse.builder()
                .totalCount(attendances.size())
                .attendCount((int) attendCount)
                .absentCount(attendances.size() - (int) attendCount)
                .attendances(details)
                .build();
    }

    // 수강생용 팩토리 메서드
    public static AttendanceResponse ofStudent(Attendance attendance) {
        return AttendanceResponse.builder()
                .totalCount(null)
                .attendCount(null)
                .absentCount(null)
                .attendances(List.of(
                        AttendanceDetail.builder()
                                .attendanceId(attendance.getId())
                                .memberId(attendance.getMemberId())
                                .status(attendance.getStatus())
                                .attendanceAt(attendance.getAttendanceAt())
                                .build()
                ))
                .build();
    }
}
