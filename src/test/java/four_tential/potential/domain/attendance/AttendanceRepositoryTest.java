//package four_tential.potential.domain.attendance;
//
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.context.annotation.Import;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.transaction.annotation.Transactional;
//import org.testcontainers.utility.TestcontainersConfiguration;
//
//
//import java.util.List;
//import java.util.Optional;
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@SpringBootTest
//@ActiveProfiles("test")
//@Import(TestcontainersConfiguration.class)
//@Transactional
//class AttendanceRepositoryTest {
//
//    @Autowired
//    private AttendanceRepository attendanceRepository;
//
//    private static final UUID COURSE_ID = UUID.randomUUID();
//    private static final UUID MEMBER_ID = UUID.randomUUID();
//    private static final UUID ORDER_ID  = UUID.randomUUID();
//
//    @Test
//    @DisplayName("코스 ID로 전체 출석 목록을 조회한다")
//    void findAllByCourseId() {
//        // given
//        attendanceRepository.save(Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID));
//        attendanceRepository.save(Attendance.register(UUID.randomUUID(), UUID.randomUUID(), COURSE_ID));
//        attendanceRepository.save(Attendance.register(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
//
//        // when
//        List<Attendance> result = attendanceRepository.findAllByCourseId(COURSE_ID);
//
//        // then
//        assertThat(result).hasSize(2);
//        assertThat(result).allMatch(a -> a.getCourseId().equals(COURSE_ID));
//    }
//
//    @Test
//    @DisplayName("memberId 와 courseId 로 출석 정보를 조회한다")
//    void findByMemberIdAndCourseId_found() {
//        // given
//        attendanceRepository.save(Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID));
//
//        // when
//        Optional<Attendance> result = attendanceRepository
//                .findByMemberIdAndCourseId(MEMBER_ID, COURSE_ID);
//
//        // then
//        assertThat(result).isPresent();
//        assertThat(result.get().getMemberId()).isEqualTo(MEMBER_ID);
//    }
//
//    @Test
//    @DisplayName("존재하지 않으면 Optional.empty() 를 반환한다")
//    void findByMemberIdAndCourseId_notFound() {
//        // when
//        Optional<Attendance> result = attendanceRepository
//                .findByMemberIdAndCourseId(MEMBER_ID, COURSE_ID);
//
//        // then
//        assertThat(result).isEmpty();
//    }
//
//    @Test
//    @DisplayName("ATTEND 상태이면 true 를 반환한다")
//    void existsByMemberIdAndCourseIdAndStatus_attend() {
//        // given
//        Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
//        attendance.attend("qr-token");
//        attendanceRepository.save(attendance);
//
//        // when & then
//        assertThat(attendanceRepository.existsByMemberIdAndCourseIdAndStatus(
//                MEMBER_ID, COURSE_ID, AttendanceStatus.ATTEND)).isTrue();
//    }
//
//    @Test
//    @DisplayName("ABSENT 상태이면 ATTEND 조건에서 false 를 반환한다")
//    void existsByMemberIdAndCourseIdAndStatus_absent() {
//        // given
//        attendanceRepository.save(Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID));
//
//        // when & then
//        assertThat(attendanceRepository.existsByMemberIdAndCourseIdAndStatus(
//                MEMBER_ID, COURSE_ID, AttendanceStatus.ATTEND)).isFalse();
//    }
//}