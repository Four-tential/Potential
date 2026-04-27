package four_tential.potential.application.attendance;

import four_tential.potential.domain.attendance.AttendanceRepository;
import four_tential.potential.presentation.attendance.dto.AttendanceListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static four_tential.potential.infra.redis.RedisConstants.ATTENDANCE_LIST_CACHE;


@Service
@RequiredArgsConstructor
public class AttendanceQueryService {

    private final AttendanceRepository attendanceRepository;

    /**
     * 출석 현황 조회 (캐시 적용)
     * 캐시 키: attendanceList::{courseId}
     * TTL: 30초
     */
    @Cacheable(
            cacheNames = ATTENDANCE_LIST_CACHE,
            key = "#courseId"
    )
    @Transactional(readOnly = true)
    public AttendanceListResponse getAttendanceSnapshot(UUID courseId) {
        return attendanceRepository.findStatsByCourseId(courseId);
    }


    //QR 스캔 출석 처리 후 캐시 무효화, scan() 완료 시 호출하여 최신 출석 현황 반영
    @CacheEvict(cacheNames = ATTENDANCE_LIST_CACHE, key = "#courseId")
    public void evict(UUID courseId) {
        // AOP가 캐시 삭제 처리
    }
}