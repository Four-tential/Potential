package four_tential.potential.presentation.attendance;

import four_tential.potential.application.attendance.AttendanceService;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.AttendanceExceptionEnum;
import four_tential.potential.domain.attendance.Attendance;
import four_tential.potential.domain.attendance.dto.AttendanceScanRequest;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceControllerTest {

    @Mock
    private AttendanceService attendanceService;

    @InjectMocks
    private AttendanceController attendanceController;

    private static final UUID COURSE_ID  = UUID.randomUUID();
    private static final UUID MEMBER_ID  = UUID.randomUUID();
    private static final UUID ORDER_ID   = UUID.randomUUID();
    private static final String QR_TOKEN = "test-qr-token";
    private static final byte[] QR_IMAGE = new byte[]{1, 2, 3};

    private MemberPrincipal instructorPrincipal;
    private MemberPrincipal studentPrincipal;

    @BeforeEach
    void setUp() {
        instructorPrincipal = new MemberPrincipal(MEMBER_ID, "instructor@test.com", "INSTRUCTOR");
        studentPrincipal    = new MemberPrincipal(MEMBER_ID, "student@test.com", "STUDENT");
    }

    @Nested
    @DisplayName("createQr() - QR 생성")
    class CreateQrTest {

        @Test
        @DisplayName("QR 생성 성공 시 200과 PNG 바이트를 반환한다")
        void createQr_success() {
            // given
            when(attendanceService.createQr(COURSE_ID, MEMBER_ID)).thenReturn(QR_IMAGE);

            // when
            ResponseEntity<byte[]> response = attendanceController.createQr(COURSE_ID, instructorPrincipal);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(QR_IMAGE);
        }

        @Test
        @DisplayName("활성 QR 이 존재하면 예외가 전파된다")
        void createQr_alreadyActive_propagatesException() {
            // given
            when(attendanceService.createQr(COURSE_ID, MEMBER_ID))
                    .thenThrow(new ServiceErrorException(AttendanceExceptionEnum.ERR_QR_ALREADY_ACTIVE));

            // when & then
            assertThatThrownBy(() -> attendanceController.createQr(COURSE_ID, instructorPrincipal))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(AttendanceExceptionEnum.ERR_QR_ALREADY_ACTIVE.getMessage());
        }
    }

    @Nested
    @DisplayName("scan() - QR 스캔 출석")
    class ScanTest {

        @Test
        @DisplayName("스캔 성공 시 200과 성공 메시지를 반환한다")
        void scan_success() {
            // given
            AttendanceScanRequest request = new AttendanceScanRequest(QR_TOKEN);
            doNothing().when(attendanceService).scan(QR_TOKEN, MEMBER_ID);

            // when
            ResponseEntity<?> response = attendanceController.scan(request, studentPrincipal);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(attendanceService).scan(QR_TOKEN, MEMBER_ID);
        }

        @Test
        @DisplayName("QR 토큰이 없으면 예외가 전파된다")
        void scan_tokenNotFound_propagatesException() {
            // given
            AttendanceScanRequest request = new AttendanceScanRequest(QR_TOKEN);
            doThrow(new ServiceErrorException(AttendanceExceptionEnum.ERR_QR_NOT_FOUND))
                    .when(attendanceService).scan(QR_TOKEN, MEMBER_ID);

            // when & then
            assertThatThrownBy(() -> attendanceController.scan(request, studentPrincipal))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(AttendanceExceptionEnum.ERR_QR_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("이미 출석 처리된 경우 예외가 전파된다")
        void scan_alreadyChecked_propagatesException() {
            // given
            AttendanceScanRequest request = new AttendanceScanRequest(QR_TOKEN);
            doThrow(new ServiceErrorException(AttendanceExceptionEnum.ERR_ALREADY_CHECKED))
                    .when(attendanceService).scan(QR_TOKEN, MEMBER_ID);

            // when & then
            assertThatThrownBy(() -> attendanceController.scan(request, studentPrincipal))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(AttendanceExceptionEnum.ERR_ALREADY_CHECKED.getMessage());
        }
    }

    @Nested
    @DisplayName("getAttendances() - 출석 현황 조회")
    class GetAttendancesTest {

        @Test
        @DisplayName("강사 조회 시 전체 출석 현황을 반환한다")
        void getAttendances_instructor_returnsAll() {
            // given
            Attendance a1 = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            Attendance a2 = Attendance.register(ORDER_ID, UUID.randomUUID(), COURSE_ID);
            a1.attend("token");
            when(attendanceService.findAllByCourse(COURSE_ID)).thenReturn(List.of(a1, a2));

            // when
            ResponseEntity<?> response = attendanceController.getAttendances(COURSE_ID, instructorPrincipal);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(attendanceService).findAllByCourse(COURSE_ID);
            verify(attendanceService, never()).findMyAttendance(any(), any());
        }

        @Test
        @DisplayName("수강생 조회 시 본인 출석 정보만 반환한다")
        void getAttendances_student_returnsMine() {
            // given
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            when(attendanceService.findMyAttendance(MEMBER_ID, COURSE_ID)).thenReturn(attendance);

            // when
            ResponseEntity<?> response = attendanceController.getAttendances(COURSE_ID, studentPrincipal);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(attendanceService).findMyAttendance(MEMBER_ID, COURSE_ID);
            verify(attendanceService, never()).findAllByCourse(any());
        }

        @Test
        @DisplayName("강사 조회 시 수강생 없으면 빈 목록을 반환한다")
        void getAttendances_instructor_emptyList() {
            // given
            when(attendanceService.findAllByCourse(COURSE_ID)).thenReturn(List.of());

            // when
            ResponseEntity<?> response = attendanceController.getAttendances(COURSE_ID, instructorPrincipal);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("수강생 출석 정보가 없으면 예외가 전파된다")
        void getAttendances_student_notFound_propagatesException() {
            // given
            when(attendanceService.findMyAttendance(MEMBER_ID, COURSE_ID))
                    .thenThrow(new ServiceErrorException(AttendanceExceptionEnum.ERR_NOT_FOUND_ATTENDANCE));

            // when & then
            assertThatThrownBy(() -> attendanceController.getAttendances(COURSE_ID, studentPrincipal))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(AttendanceExceptionEnum.ERR_NOT_FOUND_ATTENDANCE.getMessage());
        }

        @Test
        @DisplayName("강사와 수강생 role 분기가 올바르게 동작한다")
        void getAttendances_roleBranching() {
            // given
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            when(attendanceService.findAllByCourse(COURSE_ID)).thenReturn(List.of(attendance));
            when(attendanceService.findMyAttendance(MEMBER_ID, COURSE_ID)).thenReturn(attendance);

            // when
            attendanceController.getAttendances(COURSE_ID, instructorPrincipal);
            attendanceController.getAttendances(COURSE_ID, studentPrincipal);

            // then
            verify(attendanceService, times(1)).findAllByCourse(COURSE_ID);
            verify(attendanceService, times(1)).findMyAttendance(MEMBER_ID, COURSE_ID);
        }
    }
}