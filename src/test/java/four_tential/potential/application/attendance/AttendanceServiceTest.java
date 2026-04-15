package four_tential.potential.application.attendance;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.AttendanceExceptionEnum;
import four_tential.potential.domain.attendance.Attendance;
import four_tential.potential.domain.attendance.AttendanceRepository;
import four_tential.potential.domain.attendance.AttendanceStatus;
import four_tential.potential.infra.qr.QrCodeGenerator;
import four_tential.potential.infra.qr.QrTokenRepository;
import four_tential.potential.infra.sse.SseEmitterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private QrTokenRepository qrTokenRepository;

    @Mock
    private QrCodeGenerator qrCodeGenerator;

    @Mock
    private SseEmitterRepository sseEmitterRepository;

    @InjectMocks
    private AttendanceService attendanceService;

    private static final UUID COURSE_ID  = UUID.randomUUID();
    private static final UUID MEMBER_ID  = UUID.randomUUID();
    private static final UUID ORDER_ID   = UUID.randomUUID();
    private static final String QR_TOKEN = "test-qr-token";
    private static final byte[] QR_IMAGE = new byte[]{1, 2, 3};

    @Nested
    @DisplayName("createQr() - QR 생성")
    class CreateQrTest {

        @Test
        @DisplayName("정상적으로 QR 이미지를 생성하고 byte[] 를 반환한다")
        void createQr_success() {
            // given
            when(qrTokenRepository.saveIfAbsent(eq(COURSE_ID), any())).thenReturn(true);
            when(qrCodeGenerator.generate(any())).thenReturn(QR_IMAGE);

            // when
            byte[] result = attendanceService.createQr(COURSE_ID, MEMBER_ID);

            // then
            assertThat(result).isEqualTo(QR_IMAGE);
            verify(qrTokenRepository).saveIfAbsent(eq(COURSE_ID), any());
            verify(qrCodeGenerator).generate(any());
        }

        @Test
        @DisplayName("활성 QR 이 이미 존재하면 ERR_QR_ALREADY_ACTIVE 를 던진다")
        void createQr_alreadyActive_throwsException() {
            // given
            when(qrTokenRepository.saveIfAbsent(eq(COURSE_ID), any())).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> attendanceService.createQr(COURSE_ID, MEMBER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(AttendanceExceptionEnum.ERR_QR_ALREADY_ACTIVE.getMessage());

            verify(qrCodeGenerator, never()).generate(any());
        }

        @Test
        @DisplayName("QR 생성 시 Redis 에 원자적으로 저장한다")
        void createQr_savesToRedis() {
            // given
            when(qrTokenRepository.saveIfAbsent(eq(COURSE_ID), any())).thenReturn(true);
            when(qrCodeGenerator.generate(any())).thenReturn(QR_IMAGE);

            // when
            attendanceService.createQr(COURSE_ID, MEMBER_ID);

            // then
            verify(qrTokenRepository, times(1)).saveIfAbsent(eq(COURSE_ID), any());
        }
    }

    @Nested
    @DisplayName("scan() - QR 스캔 출석 처리")
    class ScanTest {

        @Test
        @DisplayName("정상적으로 출석 처리한다")
        void scan_success() {
            // given
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            when(qrTokenRepository.findCourseIdByToken(QR_TOKEN))
                    .thenReturn(Optional.of(COURSE_ID));
            when(attendanceRepository.existsByMemberIdAndCourseIdAndStatus(
                    MEMBER_ID, COURSE_ID, AttendanceStatus.ATTEND)).thenReturn(false);
            when(attendanceRepository.findByMemberIdAndCourseId(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.of(attendance));
            when(sseEmitterRepository.findByCourseId(COURSE_ID))
                    .thenReturn(Optional.empty()); // SSE 연결 없는 상황 가정

            // when
            attendanceService.scan(QR_TOKEN, MEMBER_ID);

            // then
            assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.ATTEND);
            assertThat(attendance.getQrCode()).isEqualTo(QR_TOKEN);
            assertThat(attendance.getAttendanceAt()).isNotNull();
        }

        @Test
        @DisplayName("토큰이 없으면 ERR_QR_NOT_FOUND 를 던진다")
        void scan_tokenNotFound_throwsException() {
            // given
            when(qrTokenRepository.findCourseIdByToken(QR_TOKEN))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> attendanceService.scan(QR_TOKEN, MEMBER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(AttendanceExceptionEnum.ERR_QR_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("이미 ATTEND 상태면 ERR_ALREADY_CHECKED 를 던진다")
        void scan_alreadyChecked_throwsException() {
            // given
            when(qrTokenRepository.findCourseIdByToken(QR_TOKEN))
                    .thenReturn(Optional.of(COURSE_ID));
            when(attendanceRepository.existsByMemberIdAndCourseIdAndStatus(
                    MEMBER_ID, COURSE_ID, AttendanceStatus.ATTEND)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> attendanceService.scan(QR_TOKEN, MEMBER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(AttendanceExceptionEnum.ERR_ALREADY_CHECKED.getMessage());
        }

        @Test
        @DisplayName("출석 레코드가 없으면 ERR_NOT_ENROLLED 를 던진다")
        void scan_notEnrolled_throwsException() {
            // given
            when(qrTokenRepository.findCourseIdByToken(QR_TOKEN))
                    .thenReturn(Optional.of(COURSE_ID));
            when(attendanceRepository.existsByMemberIdAndCourseIdAndStatus(
                    MEMBER_ID, COURSE_ID, AttendanceStatus.ATTEND)).thenReturn(false);
            when(attendanceRepository.findByMemberIdAndCourseId(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> attendanceService.scan(QR_TOKEN, MEMBER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(AttendanceExceptionEnum.ERR_NOT_ENROLLED.getMessage());
        }
    }

    @Nested
    @DisplayName("findAllByCourse() - 강사 전체 출석 조회")
    class FindAllByCourseTest {

        @Test
        @DisplayName("해당 코스의 전체 출석 목록을 반환한다")
        void findAllByCourse_success() {
            // given
            List<Attendance> attendances = List.of(
                    Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID),
                    Attendance.register(ORDER_ID, UUID.randomUUID(), COURSE_ID)
            );
            when(attendanceRepository.findAllByCourseId(COURSE_ID)).thenReturn(attendances);

            // when
            List<Attendance> result = attendanceService.findAllByCourse(COURSE_ID);

            // then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(a -> a.getCourseId().equals(COURSE_ID));
        }

        @Test
        @DisplayName("출석 인원이 없으면 빈 리스트를 반환한다")
        void findAllByCourse_empty() {
            // given
            when(attendanceRepository.findAllByCourseId(COURSE_ID)).thenReturn(List.of());

            // when
            List<Attendance> result = attendanceService.findAllByCourse(COURSE_ID);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findMyAttendance() - 수강생 본인 출석 조회")
    class FindMyAttendanceTest {

        @Test
        @DisplayName("본인 출석 정보를 반환한다")
        void findMyAttendance_success() {
            // given
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            when(attendanceRepository.findByMemberIdAndCourseId(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.of(attendance));

            // when
            Attendance result = attendanceService.findMyAttendance(MEMBER_ID, COURSE_ID);

            // then
            assertThat(result.getMemberId()).isEqualTo(MEMBER_ID);
            assertThat(result.getCourseId()).isEqualTo(COURSE_ID);
            assertThat(result.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        }

        @Test
        @DisplayName("출석 정보가 없으면 ERR_NOT_FOUND_ATTENDANCE 를 던진다")
        void findMyAttendance_notFound_throwsException() {
            // given
            when(attendanceRepository.findByMemberIdAndCourseId(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> attendanceService.findMyAttendance(MEMBER_ID, COURSE_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(AttendanceExceptionEnum.ERR_NOT_FOUND_ATTENDANCE.getMessage());
        }
    }
}