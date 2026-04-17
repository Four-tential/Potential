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

    // 스냅샷 조회
    @Transactional(readOnly = true)
    public AttendanceListResponse getAttendanceSnapshot(UUID courseId) {
        return attendanceRepository.findStatsByCourseId(courseId);
    }
}