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

/**
 * 출석 현황 조회 캐싱 전담 컴포넌트 (Cache-Aside 전략)
 *
 * [설계 이유]
 * 1. Self-invocation 문제 방지
 *    - @Cacheable은 AOP 프록시 기반으로 동작
 *    - AttendanceService 내부에서 직접 호출하면 프록시를 거치지 않아 캐시 미적용
 *    - 별도 빈(AttendanceQueryService)으로 분리하여 프록시를 통해 호출
 *
 * 2. 실시간성 vs 성능 균형
 *    - TTL 30초: 짧은 TTL로 최신 데이터 보장
 *    - QR 스캔 시 @CacheEvict로 즉시 무효화하여 정합성 보장
 *    - SSE 연결 강사: 실시간 이벤트로 화면 갱신 (캐시 무관)
 *    - REST 조회 강사: 캐시로 DB 부하 절감
 */
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

    /**
     * QR 스캔 출석 처리 후 캐시 무효화
     * scan() 완료 시 호출하여 최신 출석 현황 반영
     */
    @CacheEvict(cacheNames = ATTENDANCE_LIST_CACHE, key = "#courseId")
    public void evict(UUID courseId) {
        // AOP가 캐시 삭제 처리
    }
}