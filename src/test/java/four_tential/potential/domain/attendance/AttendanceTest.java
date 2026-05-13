package four_tential.potential.domain.attendance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceTest {

    private static final UUID ORDER_ID  = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final String QR_TOKEN = "test-qr-token-uuid";

    @Nested
    @DisplayName("register() - 출석 레코드 생성")
    class RegisterTest {

        @Test
        @DisplayName("정상 생성 시 orderId, memberId, courseId 가 올바르게 설정된다")
        void register_success() {
            // when
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);

            // then
            assertThat(attendance.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(attendance.getMemberId()).isEqualTo(MEMBER_ID);
            assertThat(attendance.getCourseId()).isEqualTo(COURSE_ID);
        }

        @Test
        @DisplayName("초기 상태는 ABSENT 이다")
        void register_initialStatus_isAbsent() {
            // when
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);

            // then
            assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        }

        @Test
        @DisplayName("초기 qrCode 는 null 이다")
        void register_initialQrCode_isNull() {
            // when
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);

            // then
            assertThat(attendance.getQrCode()).isNull();
        }

        @Test
        @DisplayName("초기 attendanceAt 은 null 이다")
        void register_initialAttendanceAt_isNull() {
            // when
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);

            // then
            assertThat(attendance.getAttendanceAt()).isNull();
        }

        @Test
        @DisplayName("서로 다른 수강생으로 각각 독립적인 출석 레코드가 생성된다")
        void register_multipleDifferentMembers() {
            // given
            UUID anotherMemberId = UUID.randomUUID();

            // when
            Attendance attendanceA = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            Attendance attendanceB = Attendance.register(ORDER_ID, anotherMemberId, COURSE_ID);

            // then
            assertThat(attendanceA.getMemberId()).isNotEqualTo(attendanceB.getMemberId());
            assertThat(attendanceA.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
            assertThat(attendanceB.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        }
    }

    @Nested
    @DisplayName("attend() - 출석 처리")
    class AttendTest {

        @Test
        @DisplayName("attend() 호출 시 상태가 ATTEND 로 변경된다")
        void attend_statusChangesToAttend() {
            // given
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);

            // when
            attendance.attend(QR_TOKEN);

            // then
            assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.ATTEND);
        }

        @Test
        @DisplayName("attend() 호출 시 qrCode 가 저장된다")
        void attend_qrCodeIsSaved() {
            // given
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);

            // when
            attendance.attend(QR_TOKEN);

            // then
            assertThat(attendance.getQrCode()).isEqualTo(QR_TOKEN);
        }

        @Test
        @DisplayName("attend() 호출 시 attendanceAt 이 현재 시각으로 설정된다")
        void attend_attendanceAtIsSetToNow() {
            // given
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            LocalDateTime before = LocalDateTime.now();

            // when
            attendance.attend(QR_TOKEN);

            // then
            LocalDateTime after = LocalDateTime.now();
            assertThat(attendance.getAttendanceAt())
                    .isAfterOrEqualTo(before)
                    .isBeforeOrEqualTo(after);
        }

        @Test
        @DisplayName("attend() 호출 전후 상태가 ABSENT 에서 ATTEND 로 변경된다")
        void attend_statusTransitionFromAbsentToAttend() {
            // given
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.ABSENT);

            // when
            attendance.attend(QR_TOKEN);

            // then
            assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.ATTEND);
        }

        @Test
        @DisplayName("attend() 호출 전 attendanceAt 은 null, 호출 후 null 이 아니다")
        void attend_attendanceAtNullBeforeAndNotNullAfter() {
            // given
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            assertThat(attendance.getAttendanceAt()).isNull();

            // when
            attendance.attend(QR_TOKEN);

            // then
            assertThat(attendance.getAttendanceAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("AttendanceStatus - 상태 열거형")
    class AttendanceStatusTest {

        @Test
        @DisplayName("ATTEND, ABSENT 두 가지 상태가 존재한다")
        void status_hasTwoValues() {
            assertThat(AttendanceStatus.values()).containsExactlyInAnyOrder(
                    AttendanceStatus.ATTEND,
                    AttendanceStatus.ABSENT
            );
        }

        @Test
        @DisplayName("ATTEND 상태의 name 은 'ATTEND' 이다")
        void status_attendName() {
            assertThat(AttendanceStatus.ATTEND.name()).isEqualTo("ATTEND");
        }

        @Test
        @DisplayName("ABSENT 상태의 name 은 'ABSENT' 이다")
        void status_absentName() {
            assertThat(AttendanceStatus.ABSENT.name()).isEqualTo("ABSENT");
        }
    }
}
