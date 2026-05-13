package four_tential.potential.domain.attendance;

import four_tential.potential.presentation.attendance.dto.AttendanceListResponse;

import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepositoryCustom {

    // 강사용 DB에서 직접 집계 (전체 로드 후 스트림 집계 제거)
    AttendanceListResponse findStatsByCourseId(UUID courseId);

    // 수강생용 본인 출석 단건 조회
    Optional<Attendance> findByMemberIdAndCourseIdQuery(UUID memberId, UUID courseId);

    // 중복 출석 확인
    boolean existsAttendByMemberIdAndCourseId(UUID memberId, UUID courseId);

    Optional<Attendance> findByMemberIdAndCourseIdForUpdate(UUID memberId, UUID courseId);
}