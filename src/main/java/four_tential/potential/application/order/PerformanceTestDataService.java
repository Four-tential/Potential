package four_tential.potential.application.order;

import four_tential.potential.domain.attendance.AttendanceRepository;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course_image.CourseImageRepository;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.order.OrderRepository;
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

    private static final String USER_PREFIX = "fixed_perf";
    private static final String COURSE_TITLE_PREFIX = "성능 테스트 코스";

    @Transactional
    public void deletePerformanceTestData() {
        log.info("성능 테스트 데이터 삭제 시작");

        // 1. 성능 테스트 회원 및 코스 조회
        List<Member> testMembers = memberRepository.findAllByEmailStartingWith(USER_PREFIX);
        List<Course> testCourses = courseRepository.findAllByTitleStartingWith(COURSE_TITLE_PREFIX);

        List<UUID> memberIds = testMembers.stream().map(Member::getId).collect(Collectors.toList());
        List<UUID> courseIds = testCourses.stream().map(Course::getId).collect(Collectors.toList());

        log.info("조회된 테스트 회원 수: {}, 테스트 코스 수: {}", memberIds.size(), courseIds.size());

        // 2. 출석 데이터 삭제 (회원 또는 코스 기준)
        if (!memberIds.isEmpty()) {
            attendanceRepository.deleteByMemberIdIn(memberIds);
        }
        if (!courseIds.isEmpty()) {
            attendanceRepository.deleteByCourseIdIn(courseIds);
        }

        // 3. 연관된 주문 삭제
        if (!memberIds.isEmpty()) {
            orderRepository.deleteByMemberIdIn(memberIds);
        }
        if (!courseIds.isEmpty()) {
            orderRepository.deleteByCourseIdIn(courseIds);
        }

        // 4. 코스 이미지 삭제
        if (!courseIds.isEmpty()) {
            courseImageRepository.deleteByCourseIdIn(courseIds);
        }

        // 5. Redis 데이터 정리
        for (UUID courseId : courseIds) {
            waitingListService.clearCourseRedisData(courseId, memberIds);
        }

        // 6. 코스 및 회원 삭제
        if (!courseIds.isEmpty()) {
            courseRepository.deleteByTitleStartingWith(COURSE_TITLE_PREFIX);
        }
        if (!memberIds.isEmpty()) {
            memberRepository.deleteByEmailStartingWith(USER_PREFIX);
        }

        log.info("성능 테스트 데이터 삭제 완료");
    }
}
