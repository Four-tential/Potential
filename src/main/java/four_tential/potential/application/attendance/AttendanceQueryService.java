package four_tential.potential.application.attendance;

import four_tential.potential.domain.attendance.Attendance;
import four_tential.potential.domain.attendance.AttendanceRepository;
import four_tential.potential.presentation.attendance.dto.AttendanceListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttendanceQueryService {

    private final AttendanceRepository attendanceRepository;

    // 스냅샷 조회 — AttendanceService 내부 호출 시 AOP 프록시 미적용 문제로 별도 클래스 분리
    @Transactional(readOnly = true)
    public AttendanceListResponse getAttendanceSnapshot(UUID courseId) {
        List<Attendance> attendances = attendanceRepository.findAllByCourseId(courseId);
        return AttendanceListResponse.ofInstructor(attendances);
    }
}