package four_tential.potential.application.order;

import four_tential.potential.domain.attendance.AttendanceRepository;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course_image.CourseImageRepository;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.infra.jwt.JwtRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerformanceTestDataServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CourseImageRepository courseImageRepository;
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private WaitingListService waitingListService;
    @Mock private JwtRepository jwtRepository;

    @InjectMocks
    private PerformanceTestDataService performanceTestDataService;

    @Test
    @DisplayName("성능 테스트 데이터 삭제가 정상적으로 수행된다")
    void deletePerformanceTestData_success() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Member member = mock(Member.class);
        given(member.getId()).willReturn(memberId);
        Course course = mock(Course.class);
        given(course.getId()).willReturn(courseId);

        // USER_PREFIXES 중 하나에 대해 데이터가 있다고 가정
        given(memberRepository.findAllByEmailStartingWith("fixed_perf")).willReturn(List.of(member));
        given(memberRepository.findAllByEmailStartingWith("perf_v2")).willReturn(Collections.emptyList());
        given(memberRepository.findAllByEmailStartingWith("v2_")).willReturn(Collections.emptyList());
        given(memberRepository.findAllByEmailStartingWith("p_")).willReturn(Collections.emptyList());

        // COURSE_TITLE_PREFIXES 중 하나에 대해 데이터가 있다고 가정
        given(courseRepository.findAllByTitleStartingWith("성능 테스트 코스")).willReturn(List.of(course));
        given(courseRepository.findAllByTitleStartingWith("V2 성능 테스트")).willReturn(Collections.emptyList());

        try (MockedStatic<TransactionSynchronizationManager> mockedSyncManager = mockStatic(TransactionSynchronizationManager.class)) {
            // when
            performanceTestDataService.deletePerformanceTestData();

            // then
            // 1. ID 수집 및 데이터 존재 확인
            verify(memberRepository, atLeastOnce()).findAllByEmailStartingWith(anyString());
            verify(courseRepository, atLeastOnce()).findAllByTitleStartingWith(anyString());

            // 2. 외부 상태 정리 등록 확인
            mockedSyncManager.verify(() -> TransactionSynchronizationManager.registerSynchronization(any(TransactionSynchronization.class)));

            // 3. DB 연관 데이터 삭제 확인
            verify(attendanceRepository).deleteByMemberIdIn(anyList());
            verify(orderRepository).deleteByMemberIdIn(anyList());
            verify(attendanceRepository).deleteByCourseIdIn(anyList());
            verify(orderRepository).deleteByCourseIdIn(anyList());
            verify(courseImageRepository).deleteByCourseIdIn(anyList());

            // 4. 주 엔티티 최종 삭제 확인
            verify(courseRepository, atLeastOnce()).deleteByTitleStartingWith(anyString());
            verify(memberRepository, atLeastOnce()).deleteByEmailStartingWith(anyString());
        }
    }

    @Test
    @DisplayName("정리할 데이터가 없으면 로그만 남기고 종료한다")
    void deletePerformanceTestData_noData() {
        // given
        given(memberRepository.findAllByEmailStartingWith(anyString())).willReturn(Collections.emptyList());
        given(courseRepository.findAllByTitleStartingWith(anyString())).willReturn(Collections.emptyList());

        // when
        performanceTestDataService.deletePerformanceTestData();

        // then
        verify(memberRepository, atLeastOnce()).findAllByEmailStartingWith(anyString());
        verify(courseRepository, atLeastOnce()).findAllByTitleStartingWith(anyString());
        
        // 삭제 로직이 호출되지 않아야 함
        verify(attendanceRepository, never()).deleteByMemberIdIn(anyList());
        verify(courseRepository, never()).deleteByTitleStartingWith(anyString());
    }
}
