package four_tential.potential.application.order;

import four_tential.potential.domain.attendance.AttendanceRepository;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course_image.CourseImageRepository;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.infra.jwt.JwtRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceTestDataService {

    private final MemberRepository memberRepository;
    private final CourseRepository courseRepository;
    private final OrderRepository orderRepository;
    private final CourseImageRepository courseImageRepository;
    private final AttendanceRepository attendanceRepository;
    private final WaitingListService waitingListService;
    private final JwtRepository jwtRepository;

    // 삭제 대상 접두사 리스트 (V1, V2 통합)
    private static final List<String> USER_PREFIXES = Arrays.asList("fixed_perf", "perf_v2", "v2_");
    private static final List<String> COURSE_TITLE_PREFIXES = Arrays.asList("성능 테스트 코스", "V2 성능 테스트");

    /**
     * 성능 테스트 데이터 삭제
     * 기존 Repository 메서드들을 활용하여 간결하게 구현
     */
    @Transactional
    public void deletePerformanceTestData() {
        log.info("=== 성능 테스트 데이터 클린업 시작 ===");

        // 1. 삭제 대상 ID 수집
        List<UUID> memberIds = new ArrayList<>();
        for (String prefix : USER_PREFIXES) {
            memberIds.addAll(memberRepository.findAllByEmailStartingWith(prefix).stream()
                    .map(Member::getId).toList());
        }

        List<UUID> courseIds = new ArrayList<>();
        for (String prefix : COURSE_TITLE_PREFIXES) {
            courseIds.addAll(courseRepository.findAllByTitleStartingWith(prefix).stream()
                    .map(Course::getId).toList());
        }

        if (memberIds.isEmpty() && courseIds.isEmpty()) {
            log.info("정리할 데이터가 없습니다.");
            return;
        }

        // 2. Redis 캐시 및 상태 정리
        for (String prefix : USER_PREFIXES) {
            jwtRepository.deleteRefreshTokensByPrefix(prefix);
        }
        for (UUID courseId : courseIds) {
            waitingListService.clearCourseRedisData(courseId);
        }

        // 3. DB 연관 데이터 삭제 (기존 메서드 활용)
        log.info("관련 데이터 일괄 삭제 시도 (회원: {}명, 코스: {}개)", memberIds.size(), courseIds.size());

        if (!memberIds.isEmpty()) {
            attendanceRepository.deleteByMemberIdIn(memberIds);
            orderRepository.deleteByMemberIdIn(memberIds);
        }

        if (!courseIds.isEmpty()) {
            attendanceRepository.deleteByCourseIdIn(courseIds);
            orderRepository.deleteByCourseIdIn(courseIds);
            courseImageRepository.deleteByCourseIdIn(courseIds);
        }

        // 4. 주 엔티티 최종 삭제
        for (String titlePrefix : COURSE_TITLE_PREFIXES) {
            courseRepository.deleteByTitleStartingWith(titlePrefix);
        }
        for (String userPrefix : USER_PREFIXES) {
            memberRepository.deleteByEmailStartingWith(userPrefix);
        }

        log.info("=== 성능 테스트 데이터 클린업 완료 ===");
    }
}
