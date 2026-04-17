package four_tential.potential.domain.attendance;

import four_tential.potential.infra.redis.RedisTestContainer;
import four_tential.potential.presentation.attendance.dto.AttendanceListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AttendanceRepositoryImplTest extends RedisTestContainer {

    @Autowired
    private AttendanceRepository attendanceRepository;

    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID ORDER_ID  = UUID.randomUUID();
    private static final String QR_TOKEN = "test-qr-token";

    @BeforeEach
    void setUp() {
        attendanceRepository.save(Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID));
        attendanceRepository.save(Attendance.register(UUID.randomUUID(), UUID.randomUUID(), COURSE_ID));
    }

    @Nested
    @DisplayName("findStatsByCourseId() - 출석 통계 조회")
    class FindStatsByCourseIdTest {

        @Test
        @DisplayName("출석 통계를 정상적으로 반환한다")
        void findStatsByCourseId_success() {
            // given — 1명 출석 처리
            Attendance attended = attendanceRepository
                    .findByMemberIdAndCourseIdQuery(MEMBER_ID, COURSE_ID).orElseThrow();
            attended.attend(QR_TOKEN);

            // when
            AttendanceListResponse result = attendanceRepository.findStatsByCourseId(COURSE_ID);

            // then
            assertThat(result.getTotalCount()).isEqualTo(2);
            assertThat(result.getAttendCount()).isEqualTo(1);
            assertThat(result.getAbsentCount()).isEqualTo(1);
            assertThat(result.getAttendances()).hasSize(2);
        }

        @Test
        @DisplayName("전원 ABSENT 이면 attendCount 는 0 이다")
        void findStatsByCourseId_allAbsent() {
            // when
            AttendanceListResponse result = attendanceRepository.findStatsByCourseId(COURSE_ID);

            // then
            assertThat(result.getTotalCount()).isEqualTo(2);
            assertThat(result.getAttendCount()).isZero();
            assertThat(result.getAbsentCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("수강생이 없으면 모든 집계가 0 이다")
        void findStatsByCourseId_empty() {
            // when
            AttendanceListResponse result = attendanceRepository.findStatsByCourseId(UUID.randomUUID());

            // then
            assertThat(result.getTotalCount()).isZero();
            assertThat(result.getAttendCount()).isZero();
            assertThat(result.getAbsentCount()).isZero();
            assertThat(result.getAttendances()).isEmpty();
        }

        @Test
        @DisplayName("ATTEND 상태인 경우 attendanceAt 이 존재한다")
        void findStatsByCourseId_attendDetail() {
            // given
            Attendance attended = attendanceRepository
                    .findByMemberIdAndCourseIdQuery(MEMBER_ID, COURSE_ID).orElseThrow();
            attended.attend(QR_TOKEN);

            // when
            AttendanceListResponse result = attendanceRepository.findStatsByCourseId(COURSE_ID);

            // then
            result.getAttendances().stream()
                    .filter(d -> d.getMemberId().equals(MEMBER_ID))
                    .findFirst()
                    .ifPresent(d -> {
                        assertThat(d.getStatus()).isEqualTo(AttendanceStatus.ATTEND);
                        assertThat(d.getAttendanceAt()).isNotNull();
                    });
        }
    }

    @Nested
    @DisplayName("findByMemberIdAndCourseIdQuery() - 본인 출석 단건 조회")
    class FindByMemberIdAndCourseIdQueryTest {

        @Test
        @DisplayName("존재하면 Attendance 를 반환한다")
        void findByMemberIdAndCourseIdQuery_found() {
            // when
            Optional<Attendance> result = attendanceRepository
                    .findByMemberIdAndCourseIdQuery(MEMBER_ID, COURSE_ID);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getMemberId()).isEqualTo(MEMBER_ID);
            assertThat(result.get().getCourseId()).isEqualTo(COURSE_ID);
        }

        @Test
        @DisplayName("존재하지 않으면 Optional.empty() 를 반환한다")
        void findByMemberIdAndCourseIdQuery_notFound() {
            // when
            Optional<Attendance> result = attendanceRepository
                    .findByMemberIdAndCourseIdQuery(UUID.randomUUID(), COURSE_ID);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsAttendByMemberIdAndCourseId() - 출석 여부 확인")
    class ExistsAttendByMemberIdAndCourseIdTest {

        @Test
        @DisplayName("ATTEND 상태이면 true 를 반환한다")
        void existsAttend_returnsTrue() {
            // given
            Attendance attendance = attendanceRepository
                    .findByMemberIdAndCourseIdQuery(MEMBER_ID, COURSE_ID).orElseThrow();
            attendance.attend(QR_TOKEN);

            // when & then
            assertThat(attendanceRepository.existsAttendByMemberIdAndCourseId(MEMBER_ID, COURSE_ID))
                    .isTrue();
        }

        @Test
        @DisplayName("ABSENT 상태이면 false 를 반환한다")
        void existsAttend_absent_returnsFalse() {
            // when & then
            assertThat(attendanceRepository.existsAttendByMemberIdAndCourseId(MEMBER_ID, COURSE_ID))
                    .isFalse();
        }

        @Test
        @DisplayName("레코드가 없으면 false 를 반환한다")
        void existsAttend_noRecord_returnsFalse() {
            // when & then
            assertThat(attendanceRepository.existsAttendByMemberIdAndCourseId(UUID.randomUUID(), COURSE_ID))
                    .isFalse();
        }
    }
}