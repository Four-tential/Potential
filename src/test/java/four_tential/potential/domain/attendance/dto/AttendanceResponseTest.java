package four_tential.potential.domain.attendance.dto;

import four_tential.potential.domain.attendance.Attendance;
import four_tential.potential.domain.attendance.AttendanceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceResponseTest {

    private static final UUID ORDER_ID  = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();

    @Nested
    @DisplayName("ofInstructor() - 강사용 응답 생성")
    class OfInstructorTest {

        @Test
        @DisplayName("전체 수강생 수가 올바르게 집계된다")
        void ofInstructor_totalCount() {
            // given
            Attendance a1 = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            Attendance a2 = Attendance.register(ORDER_ID, UUID.randomUUID(), COURSE_ID);
            a1.attend("token");

            // when
            AttendanceResponse response = AttendanceResponse.ofInstructor(List.of(a1, a2));

            // then
            assertThat(response.getTotalCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("출석 인원과 미출석 인원이 올바르게 집계된다")
        void ofInstructor_attendAndAbsentCount() {
            // given
            Attendance a1 = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            Attendance a2 = Attendance.register(ORDER_ID, UUID.randomUUID(), COURSE_ID);
            Attendance a3 = Attendance.register(ORDER_ID, UUID.randomUUID(), COURSE_ID);
            a1.attend("token-1");
            a2.attend("token-2");

            // when
            AttendanceResponse response = AttendanceResponse.ofInstructor(List.of(a1, a2, a3));

            // then
            assertThat(response.getAttendCount()).isEqualTo(2);
            assertThat(response.getAbsentCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("출석 목록이 상세 정보를 포함한다")
        void ofInstructor_attendanceDetails() {
            // given
            Attendance a1 = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            a1.attend("token");

            // when
            AttendanceResponse response = AttendanceResponse.ofInstructor(List.of(a1));

            // then
            assertThat(response.getAttendances()).hasSize(1);
            assertThat(response.getAttendances().get(0).getMemberId()).isEqualTo(MEMBER_ID);
            assertThat(response.getAttendances().get(0).getStatus()).isEqualTo(AttendanceStatus.ATTEND);
            assertThat(response.getAttendances().get(0).getAttendanceAt()).isNotNull();
        }

        @Test
        @DisplayName("수강생이 없으면 모든 집계가 0이다")
        void ofInstructor_emptyList() {
            // when
            AttendanceResponse response = AttendanceResponse.ofInstructor(List.of());

            // then
            assertThat(response.getTotalCount()).isZero();
            assertThat(response.getAttendCount()).isZero();
            assertThat(response.getAbsentCount()).isZero();
            assertThat(response.getAttendances()).isEmpty();
        }

        @Test
        @DisplayName("모두 ABSENT 이면 attendCount 는 0 이다")
        void ofInstructor_allAbsent() {
            // given
            Attendance a1 = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            Attendance a2 = Attendance.register(ORDER_ID, UUID.randomUUID(), COURSE_ID);

            // when
            AttendanceResponse response = AttendanceResponse.ofInstructor(List.of(a1, a2));

            // then
            assertThat(response.getAttendCount()).isZero();
            assertThat(response.getAbsentCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("ofStudent() - 수강생용 응답 생성")
    class OfStudentTest {

        @Test
        @DisplayName("집계 필드는 모두 null 이다")
        void ofStudent_aggregateFieldsAreNull() {
            // given
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);

            // when
            AttendanceResponse response = AttendanceResponse.ofStudent(attendance);

            // then
            assertThat(response.getTotalCount()).isNull();
            assertThat(response.getAttendCount()).isNull();
            assertThat(response.getAbsentCount()).isNull();
        }

        @Test
        @DisplayName("출석 목록에 본인 단건만 포함된다")
        void ofStudent_singleAttendance() {
            // given
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);

            // when
            AttendanceResponse response = AttendanceResponse.ofStudent(attendance);

            // then
            assertThat(response.getAttendances()).hasSize(1);
            assertThat(response.getAttendances().get(0).getMemberId()).isEqualTo(MEMBER_ID);
        }

        @Test
        @DisplayName("ABSENT 상태인 경우 상태와 attendanceAt 이 올바르게 반환된다")
        void ofStudent_absentStatus() {
            // given
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);

            // when
            AttendanceResponse response = AttendanceResponse.ofStudent(attendance);

            // then
            assertThat(response.getAttendances().get(0).getStatus()).isEqualTo(AttendanceStatus.ABSENT);
            assertThat(response.getAttendances().get(0).getAttendanceAt()).isNull();
        }

        @Test
        @DisplayName("ATTEND 상태인 경우 attendanceAt 이 존재한다")
        void ofStudent_attendStatus() {
            // given
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            attendance.attend("qr-token");

            // when
            AttendanceResponse response = AttendanceResponse.ofStudent(attendance);

            // then
            assertThat(response.getAttendances().get(0).getStatus()).isEqualTo(AttendanceStatus.ATTEND);
            assertThat(response.getAttendances().get(0).getAttendanceAt()).isNotNull();
        }
    }
}