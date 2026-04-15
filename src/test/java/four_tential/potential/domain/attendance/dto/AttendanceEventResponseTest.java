package four_tential.potential.domain.attendance.dto;

import four_tential.potential.domain.attendance.Attendance;
import four_tential.potential.domain.attendance.AttendanceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceEventResponseTest {

    private static final UUID ORDER_ID  = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();

    @Test
    @DisplayName("ABSENT 상태의 출석 정보로 이벤트 응답을 생성한다")
    void from_absent() {
        // given
        Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);

        // when
        AttendanceEventResponse response = AttendanceEventResponse.from(attendance);

        // then
        assertThat(response.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(response.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(response.getAttendanceAt()).isNull();
    }

    @Test
    @DisplayName("ATTEND 상태의 출석 정보로 이벤트 응답을 생성한다")
    void from_attend() {
        // given
        Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
        attendance.attend("qr-token");

        // when
        AttendanceEventResponse response = AttendanceEventResponse.from(attendance);

        // then
        assertThat(response.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(response.getStatus()).isEqualTo(AttendanceStatus.ATTEND);
        assertThat(response.getAttendanceAt()).isNotNull();
    }
}