package four_tential.potential.application.attendance;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.AttendanceExceptionEnum;
import four_tential.potential.common.exception.domain.CourseExceptionEnum;
import four_tential.potential.domain.attendance.Attendance;
import four_tential.potential.domain.attendance.AttendanceRepository;
import four_tential.potential.domain.attendance.AttendanceStatus;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.presentation.attendance.dto.AttendanceListResponse;
import four_tential.potential.infra.qr.QrCodeGenerator;
import four_tential.potential.infra.qr.QrTokenRepository;
import four_tential.potential.infra.sse.SseAttendanceEventPublisher;
import four_tential.potential.infra.sse.SseEmitterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock private AttendanceRepository attendanceRepository;
    @Mock private QrTokenRepository qrTokenRepository;
    @Mock private QrCodeGenerator qrCodeGenerator;
    @Mock private SseEmitterRepository sseEmitterRepository;
    @Mock private AttendanceQueryService attendanceQueryService;
    @Mock private SseAttendanceEventPublisher sseAttendanceEventPublisher;
    @Mock private CourseRepository courseRepository;
    @Mock private OrderRepository orderRepository;

    @InjectMocks
    private AttendanceService attendanceService;

    private static final UUID COURSE_ID  = UUID.randomUUID();
    private static final UUID MEMBER_ID  = UUID.randomUUID();
    private static final UUID ORDER_ID   = UUID.randomUUID();
    private static final String QR_TOKEN = "test-qr-token";
    private static final byte[] QR_IMAGE = new byte[]{1, 2, 3};

    private Course makeCourse(UUID instructorId, LocalDateTime startAt, CourseStatus status) {
        try {
            var constructor = Course.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            Course course = (Course) constructor.newInstance();
            setField(course, "memberInstructorId", instructorId);
            setField(course, "startAt", startAt);
            setField(course, "status", status);
            return course;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        var field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    @Nested
    @DisplayName("createQr() - QR 생성")
    class CreateQrTest {

        @Test
        @DisplayName("정상적으로 QR 이미지를 생성하고 byte[] 를 반환한다")
        void createQr_success() {
            Course course = makeCourse(MEMBER_ID, LocalDateTime.now().minusMinutes(5), CourseStatus.OPEN);
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            when(qrTokenRepository.saveIfAbsent(eq(COURSE_ID), any())).thenReturn(true);
            when(qrCodeGenerator.generate(any())).thenReturn(QR_IMAGE);

            byte[] result = attendanceService.createQr(COURSE_ID, MEMBER_ID);

            assertThat(result).isEqualTo(QR_IMAGE);
            verify(qrTokenRepository).saveIfAbsent(eq(COURSE_ID), any());
            verify(qrCodeGenerator).generate(any());
        }

        @Test
        @DisplayName("코스가 존재하지 않으면 ERR_NOT_FOUND_COURSE 를 던진다")
        void createQr_courseNotFound_throwsException() {
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> attendanceService.createQr(COURSE_ID, MEMBER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(CourseExceptionEnum.ERR_NOT_FOUND_COURSE.getMessage());
        }

        @Test
        @DisplayName("강사 본인 코스가 아니면 ERR_QR_FORBIDDEN 을 던진다")
        void createQr_notOwnCourse_throwsException() {
            Course course = makeCourse(UUID.randomUUID(), LocalDateTime.now().minusMinutes(5), CourseStatus.OPEN);
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));

            assertThatThrownBy(() -> attendanceService.createQr(COURSE_ID, MEMBER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(AttendanceExceptionEnum.ERR_QR_FORBIDDEN.getMessage());
        }

        @Test
        @DisplayName("코스 상태가 OPEN 이 아니면 ERR_COURSE_NOT_OPEN 을 던진다")
        void createQr_courseNotOpen_throwsException() {
            Course course = makeCourse(MEMBER_ID, LocalDateTime.now().minusMinutes(5), CourseStatus.PREPARATION);
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));

            assertThatThrownBy(() -> attendanceService.createQr(COURSE_ID, MEMBER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(CourseExceptionEnum.ERR_COURSE_NOT_OPEN.getMessage());

            verify(qrCodeGenerator, never()).generate(any());
        }

        @Test
        @DisplayName("코스 상태가 CLOSED 이면 ERR_COURSE_NOT_OPEN 을 던진다")
        void createQr_courseClosed_throwsException() {
            Course course = makeCourse(MEMBER_ID, LocalDateTime.now().minusMinutes(5), CourseStatus.CLOSED);
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));

            assertThatThrownBy(() -> attendanceService.createQr(COURSE_ID, MEMBER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(CourseExceptionEnum.ERR_COURSE_NOT_OPEN.getMessage());
        }

        @Test
        @DisplayName("코스 시작 전이면 ERR_QR_NOT_STARTED 를 던진다")
        void createQr_beforeStart_throwsException() {
            Course course = makeCourse(MEMBER_ID, LocalDateTime.now().plusHours(1), CourseStatus.OPEN);
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));

            assertThatThrownBy(() -> attendanceService.createQr(COURSE_ID, MEMBER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(AttendanceExceptionEnum.ERR_QR_NOT_STARTED.getMessage());
        }

        @Test
        @DisplayName("코스 시작 후 10분 초과면 ERR_QR_EXPIRED_WINDOW 를 던진다")
        void createQr_expiredWindow_throwsException() {
            Course course = makeCourse(MEMBER_ID, LocalDateTime.now().minusMinutes(11), CourseStatus.OPEN);
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));

            assertThatThrownBy(() -> attendanceService.createQr(COURSE_ID, MEMBER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(AttendanceExceptionEnum.ERR_QR_EXPIRED_WINDOW.getMessage());
        }

        @Test
        @DisplayName("활성 QR 이 이미 존재하면 ERR_QR_ALREADY_ACTIVE 를 던진다")
        void createQr_alreadyActive_throwsException() {
            Course course = makeCourse(MEMBER_ID, LocalDateTime.now().minusMinutes(5), CourseStatus.OPEN);
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            when(qrTokenRepository.saveIfAbsent(eq(COURSE_ID), any())).thenReturn(false);

            assertThatThrownBy(() -> attendanceService.createQr(COURSE_ID, MEMBER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(AttendanceExceptionEnum.ERR_QR_ALREADY_ACTIVE.getMessage());

            verify(qrCodeGenerator, never()).generate(any());
        }

        @Test
        @DisplayName("QR 생성 시 Redis 에 원자적으로 저장한다")
        void createQr_savesToRedis() {
            Course course = makeCourse(MEMBER_ID, LocalDateTime.now().minusMinutes(5), CourseStatus.OPEN);
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            when(qrTokenRepository.saveIfAbsent(eq(COURSE_ID), any())).thenReturn(true);
            when(qrCodeGenerator.generate(any())).thenReturn(QR_IMAGE);

            attendanceService.createQr(COURSE_ID, MEMBER_ID);

            verify(qrTokenRepository, times(1)).saveIfAbsent(eq(COURSE_ID), any());
        }
    }

    @Nested
    @DisplayName("scan() - QR 스캔 출석 처리")
    class ScanTest {

        @Test
        @DisplayName("정상적으로 출석 처리한다")
        void scan_success() {
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            when(qrTokenRepository.findCourseIdByToken(QR_TOKEN)).thenReturn(Optional.of(COURSE_ID));
            when(orderRepository.existsByMemberIdAndCourseIdAndStatus(
                    MEMBER_ID, COURSE_ID, OrderStatus.CONFIRMED)).thenReturn(true);
            when(attendanceRepository.findByMemberIdAndCourseIdForUpdate(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.of(attendance));

            TransactionSynchronizationManager.initSynchronization();
            try {
                attendanceService.scan(QR_TOKEN, MEMBER_ID);

                assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.ATTEND);
                assertThat(attendance.getQrCode()).isEqualTo(QR_TOKEN);
                assertThat(attendance.getAttendanceAt()).isNotNull();
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        @Test
        @DisplayName("토큰이 없으면 ERR_QR_NOT_FOUND 를 던진다")
        void scan_tokenNotFound_throwsException() {
            when(qrTokenRepository.findCourseIdByToken(QR_TOKEN)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> attendanceService.scan(QR_TOKEN, MEMBER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(AttendanceExceptionEnum.ERR_QR_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("예약이 확정되지 않으면 ERR_ORDER_NOT_CONFIRMED 를 던진다")
        void scan_orderNotConfirmed_throwsException() {
            when(qrTokenRepository.findCourseIdByToken(QR_TOKEN)).thenReturn(Optional.of(COURSE_ID));
            when(orderRepository.existsByMemberIdAndCourseIdAndStatus(
                    MEMBER_ID, COURSE_ID, OrderStatus.CONFIRMED)).thenReturn(false);

            assertThatThrownBy(() -> attendanceService.scan(QR_TOKEN, MEMBER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(AttendanceExceptionEnum.ERR_ORDER_NOT_CONFIRMED.getMessage());
        }

        @Test
        @DisplayName("이미 ATTEND 상태면 ERR_ALREADY_CHECKED 를 던진다")
        void scan_alreadyChecked_throwsException() {
            // ATTEND 상태인 attendance 생성
            Attendance attended = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            attended.attend(QR_TOKEN);

            when(qrTokenRepository.findCourseIdByToken(QR_TOKEN)).thenReturn(Optional.of(COURSE_ID));
            when(orderRepository.existsByMemberIdAndCourseIdAndStatus(
                    MEMBER_ID, COURSE_ID, OrderStatus.CONFIRMED)).thenReturn(true);
            when(attendanceRepository.findByMemberIdAndCourseIdForUpdate(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.of(attended));

            assertThatThrownBy(() -> attendanceService.scan(QR_TOKEN, MEMBER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(AttendanceExceptionEnum.ERR_ALREADY_CHECKED.getMessage());
        }

        @Test
        @DisplayName("출석 레코드가 없으면 ERR_NOT_ENROLLED 를 던진다")
        void scan_notEnrolled_throwsException() {
            when(qrTokenRepository.findCourseIdByToken(QR_TOKEN)).thenReturn(Optional.of(COURSE_ID));
            when(orderRepository.existsByMemberIdAndCourseIdAndStatus(
                    MEMBER_ID, COURSE_ID, OrderStatus.CONFIRMED)).thenReturn(true);
            when(attendanceRepository.findByMemberIdAndCourseIdForUpdate(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.empty());

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
            Course course = makeCourse(MEMBER_ID, LocalDateTime.now().minusMinutes(5), CourseStatus.OPEN);
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            List<Attendance> attendances = List.of(
                    Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID),
                    Attendance.register(ORDER_ID, UUID.randomUUID(), COURSE_ID)
            );
            when(attendanceRepository.findStatsByCourseId(COURSE_ID))
                    .thenReturn(AttendanceListResponse.ofInstructor(attendances));

            AttendanceListResponse result = attendanceService.findAllByCourse(COURSE_ID, MEMBER_ID);

            assertThat(result.getTotalCount()).isEqualTo(2);
            assertThat(result.getAttendances()).hasSize(2);
        }

        @Test
        @DisplayName("코스가 존재하지 않으면 ERR_NOT_FOUND_COURSE 를 던진다")
        void findAllByCourse_courseNotFound_throwsException() {
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> attendanceService.findAllByCourse(COURSE_ID, MEMBER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(CourseExceptionEnum.ERR_NOT_FOUND_COURSE.getMessage());
        }

        @Test
        @DisplayName("본인 코스가 아니면 ERR_QR_FORBIDDEN 을 던진다")
        void findAllByCourse_notOwnCourse_throwsException() {
            Course course = makeCourse(UUID.randomUUID(), LocalDateTime.now().minusMinutes(5), CourseStatus.OPEN);
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));

            assertThatThrownBy(() -> attendanceService.findAllByCourse(COURSE_ID, MEMBER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(AttendanceExceptionEnum.ERR_QR_FORBIDDEN.getMessage());
        }

        @Test
        @DisplayName("출석 인원이 없으면 빈 목록을 반환한다")
        void findAllByCourse_empty() {
            Course course = makeCourse(MEMBER_ID, LocalDateTime.now().minusMinutes(5), CourseStatus.OPEN);
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            when(attendanceRepository.findStatsByCourseId(COURSE_ID))
                    .thenReturn(AttendanceListResponse.ofInstructor(List.of()));

            AttendanceListResponse result = attendanceService.findAllByCourse(COURSE_ID, MEMBER_ID);

            assertThat(result.getTotalCount()).isZero();
            assertThat(result.getAttendances()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findMyAttendance() - 수강생 본인 출석 조회")
    class FindMyAttendanceTest {

        @Test
        @DisplayName("본인 출석 정보를 반환한다")
        void findMyAttendance_success() {
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            when(attendanceRepository.findByMemberIdAndCourseIdQuery(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.of(attendance));

            Attendance result = attendanceService.findMyAttendance(MEMBER_ID, COURSE_ID);

            assertThat(result.getMemberId()).isEqualTo(MEMBER_ID);
            assertThat(result.getCourseId()).isEqualTo(COURSE_ID);
            assertThat(result.getStatus()).isEqualTo(four_tential.potential.domain.attendance.AttendanceStatus.ABSENT);
        }

        @Test
        @DisplayName("출석 정보가 없으면 ERR_NOT_FOUND_ATTENDANCE 를 던진다")
        void findMyAttendance_notFound_throwsException() {
            when(attendanceRepository.findByMemberIdAndCourseIdQuery(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> attendanceService.findMyAttendance(MEMBER_ID, COURSE_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(AttendanceExceptionEnum.ERR_NOT_FOUND_ATTENDANCE.getMessage());
        }
    }

    @Nested
    @DisplayName("stream() - SSE 스트림 연결")
    class StreamTest {

        @Test
        @DisplayName("SSE 연결 성공 시 SseEmitter 를 반환한다")
        void stream_success() {
            Course course = makeCourse(MEMBER_ID, LocalDateTime.now().minusMinutes(5), CourseStatus.OPEN);
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            when(attendanceQueryService.getAttendanceSnapshot(COURSE_ID))
                    .thenReturn(AttendanceListResponse.ofInstructor(List.of(
                            Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID)
                    )));

            SseEmitter emitter = attendanceService.stream(COURSE_ID, MEMBER_ID);

            assertThat(emitter).isNotNull();
            verify(sseEmitterRepository).save(eq(COURSE_ID), any(SseEmitter.class));
        }

        @Test
        @DisplayName("코스가 존재하지 않으면 ERR_NOT_FOUND_COURSE 를 던진다")
        void stream_courseNotFound_throwsException() {
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> attendanceService.stream(COURSE_ID, MEMBER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(CourseExceptionEnum.ERR_NOT_FOUND_COURSE.getMessage());
        }

        @Test
        @DisplayName("본인 코스가 아니면 ERR_QR_FORBIDDEN 을 던진다")
        void stream_notOwnCourse_throwsException() {
            Course course = makeCourse(UUID.randomUUID(), LocalDateTime.now().minusMinutes(5), CourseStatus.OPEN);
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));

            assertThatThrownBy(() -> attendanceService.stream(COURSE_ID, MEMBER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(AttendanceExceptionEnum.ERR_QR_FORBIDDEN.getMessage());
        }

        @Test
        @DisplayName("코스 상태가 OPEN 이 아니면 ERR_COURSE_NOT_OPEN 을 던진다")
        void stream_courseNotOpen_throwsException() {
            Course course = makeCourse(MEMBER_ID, LocalDateTime.now().minusMinutes(5), CourseStatus.CLOSED);
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));

            assertThatThrownBy(() -> attendanceService.stream(COURSE_ID, MEMBER_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(CourseExceptionEnum.ERR_COURSE_NOT_OPEN.getMessage());

            verify(sseEmitterRepository, never()).save(any(), any());
        }

        @Test
        @DisplayName("SSE 연결 시 수강생이 없어도 빈 스냅샷을 전송한다")
        void stream_emptySnapshot() {
            Course course = makeCourse(MEMBER_ID, LocalDateTime.now().minusMinutes(5), CourseStatus.OPEN);
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            when(attendanceQueryService.getAttendanceSnapshot(COURSE_ID))
                    .thenReturn(AttendanceListResponse.ofInstructor(List.of()));

            SseEmitter emitter = attendanceService.stream(COURSE_ID, MEMBER_ID);

            assertThat(emitter).isNotNull();
            verify(sseEmitterRepository).save(eq(COURSE_ID), any(SseEmitter.class));
        }

        @Test
        @DisplayName("스냅샷 조회 중 Exception 발생 시 예외를 던진다")
        void stream_snapshotException_throwsException() {
            Course course = makeCourse(MEMBER_ID, LocalDateTime.now().minusMinutes(5), CourseStatus.OPEN);
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            when(attendanceQueryService.getAttendanceSnapshot(COURSE_ID))
                    .thenThrow(new RuntimeException("DB 오류"));

            assertThatThrownBy(() -> attendanceService.stream(COURSE_ID, MEMBER_ID))
                    .isInstanceOf(RuntimeException.class);

            verify(sseEmitterRepository).save(eq(COURSE_ID), any(SseEmitter.class));
            verify(sseEmitterRepository).deleteIfSame(eq(COURSE_ID), any(SseEmitter.class));
        }
    }

    @Test
    @DisplayName("scan() 성공 시 afterCommit 콜백이 등록된다")
    void scan_registersAfterCommitCallback() {
        Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
        when(qrTokenRepository.findCourseIdByToken(QR_TOKEN)).thenReturn(Optional.of(COURSE_ID));
        when(orderRepository.existsByMemberIdAndCourseIdAndStatus(
                MEMBER_ID, COURSE_ID, OrderStatus.CONFIRMED)).thenReturn(true);
        when(attendanceRepository.findByMemberIdAndCourseIdForUpdate(MEMBER_ID, COURSE_ID))
                .thenReturn(Optional.of(attendance));

        TransactionSynchronizationManager.initSynchronization();
        try {
            attendanceService.scan(QR_TOKEN, MEMBER_ID);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCommit());

            verify(sseAttendanceEventPublisher).publish(eq(COURSE_ID), any(Attendance.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}