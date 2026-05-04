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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

    private static final String USER_PREFIX = "fixed_perf";
    private static final String COURSE_TITLE_PREFIX = "성능 테스트 코스";

    /**
     * 성능 테스트 데이터 삭제
     * Redis 데이터 정리를 먼저 수행하여 DB 트랜잭션 실패와 무관하게 처리되도록 함
     */
    @Transactional
    public void deletePerformanceTestData() {
        log.info("=== 성능 테스트 데이터 삭제 시작 ===");

        // 1. 대상 조회
        List<Member> testMembers = memberRepository.findAllByEmailStartingWith(USER_PREFIX);
        List<Course> testCourses = courseRepository.findAllByTitleStartingWith(COURSE_TITLE_PREFIX);

        List<UUID> memberIds = testMembers.stream().map(Member::getId).collect(Collectors.toList());
        List<UUID> courseIds = testCourses.stream().map(Course::getId).collect(Collectors.toList());

        log.info("조회된 테스트 회원: {}명, 테스트 코스: {}개", memberIds.size(), courseIds.size());

        // 2. Redis 데이터 우선 삭제 (트랜잭션에 덜 민감한 데이터)
        log.info("[Redis] 리프레시 토큰 삭제 시도 (Prefix: {})", USER_PREFIX);
        jwtRepository.deleteRefreshTokensByPrefix(USER_PREFIX);

        if (!courseIds.isEmpty()) {
            log.info("[Redis] 코스 관련 데이터(대기열, 점유정보) 삭제 시도 (코스 수: {})", courseIds.size());
            for (UUID courseId : courseIds) {
                waitingListService.clearCourseRedisData(courseId);
            }
        }

        // 3. DB 데이터 삭제 (외래 키 관계 고려하여 자식부터 삭제)
        if (!memberIds.isEmpty() || !courseIds.isEmpty()) {
            log.info("[DB] 출석 데이터 삭제");
            if (!memberIds.isEmpty()) attendanceRepository.deleteByMemberIdIn(memberIds);
            if (!courseIds.isEmpty()) attendanceRepository.deleteByCourseIdIn(courseIds);

            log.info("[DB] 주문 데이터 삭제");
            if (!memberIds.isEmpty()) orderRepository.deleteByMemberIdIn(memberIds);
            if (!courseIds.isEmpty()) orderRepository.deleteByCourseIdIn(courseIds);

            log.info("[DB] 코스 이미지 삭제");
            if (!courseIds.isEmpty()) courseImageRepository.deleteByCourseIdIn(courseIds);

            log.info("[DB] 코스 및 회원 삭제");
            if (!courseIds.isEmpty()) {
                courseRepository.deleteByTitleStartingWith(COURSE_TITLE_PREFIX);
            }
            if (!memberIds.isEmpty()) {
                memberRepository.deleteByEmailStartingWith(USER_PREFIX);
            }
        }

        log.info("=== 성능 테스트 데이터 삭제 완료 ===");
    }
}
