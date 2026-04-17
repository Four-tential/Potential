package four_tential.potential.application.attendance;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.AttendanceExceptionEnum;
import four_tential.potential.common.exception.domain.CourseExceptionEnum;
import four_tential.potential.domain.attendance.Attendance;
import four_tential.potential.domain.attendance.AttendanceRepository;
import four_tential.potential.domain.attendance.AttendanceStatus;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.presentation.attendance.dto.AttendanceListResponse;
import four_tential.potential.infra.qr.QrCodeGenerator;
import four_tential.potential.infra.qr.QrTokenRepository;
import four_tential.potential.infra.sse.SseAttendanceEventPublisher;
import four_tential.potential.infra.sse.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final QrTokenRepository    qrTokenRepository;
    private final QrCodeGenerator      qrCodeGenerator;
    private final SseEmitterRepository sseEmitterRepository;
    private final AttendanceQueryService attendanceQueryService;
    private final SseAttendanceEventPublisher sseAttendanceEventPublisher;
    private final CourseRepository courseRepository;
    private final OrderRepository orderRepository;

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30분
    private static final long QR_OPEN_MINUTES   = 10L;  // QR 생성 가능 시간

    // QR 생성(강사 전용)
    public byte[] createQr(UUID courseId, UUID instructorId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(CourseExceptionEnum.ERR_NOT_FOUND_COURSE));

        // 강사 본인 코스인지 검증
        if (!course.getMemberInstructorId().equals(instructorId)) {
            throw new ServiceErrorException(AttendanceExceptionEnum.ERR_QR_FORBIDDEN);
        }

        // 코스 시작 이후인지 검증
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(course.getStartAt())) {
            throw new ServiceErrorException(AttendanceExceptionEnum.ERR_QR_NOT_STARTED);
        }

        // 코스 시작 후 10분 이내인지 검증
        if (now.isAfter(course.getStartAt().plusMinutes(QR_OPEN_MINUTES))) {
            throw new ServiceErrorException(AttendanceExceptionEnum.ERR_QR_EXPIRED_WINDOW);
        }

        String token = UUID.randomUUID().toString();

        // SETNX 원자적 연산: 중복 확인 + 저장을 한 번에 처리
        boolean saved = qrTokenRepository.saveIfAbsent(courseId, token);
        if (!saved) {
            throw new ServiceErrorException(AttendanceExceptionEnum.ERR_QR_ALREADY_ACTIVE);
        }

        return qrCodeGenerator.generate(token);
    }

    // QR 스캔 출석 처리(수강생 전용)
    @Transactional
    public void scan(String qrToken, UUID memberId) {
        UUID courseId = qrTokenRepository.findCourseIdByToken(qrToken)
                .orElseThrow(() -> new ServiceErrorException(AttendanceExceptionEnum.ERR_QR_NOT_FOUND));

        if (attendanceRepository.existsByMemberIdAndCourseIdAndStatus(
                memberId, courseId, AttendanceStatus.ATTEND)) {
            throw new ServiceErrorException(AttendanceExceptionEnum.ERR_ALREADY_CHECKED);
        }

        // 예약 확정 여부 검증 추가
        boolean isConfirmed = orderRepository.existsByMemberIdAndCourseIdAndStatus(
                memberId, courseId, OrderStatus.CONFIRMED);
        if (!isConfirmed) {
            throw new ServiceErrorException(AttendanceExceptionEnum.ERR_ORDER_NOT_CONFIRMED);
        }

        Attendance attendance = attendanceRepository.findByMemberIdAndCourseId(memberId, courseId)
                .orElseThrow(() -> new ServiceErrorException(AttendanceExceptionEnum.ERR_NOT_ENROLLED));

        attendance.attend(qrToken);

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        sseAttendanceEventPublisher.publish(courseId, attendance);
                    }
                }
        );
    }

    // 출석 현황 조회 (강사 -> 전체)
    @Transactional(readOnly = true)
    public List<Attendance> findAllByCourse(UUID courseId, UUID instructorId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(CourseExceptionEnum.ERR_NOT_FOUND_COURSE));

        if (!course.getMemberInstructorId().equals(instructorId)) {
            throw new ServiceErrorException(AttendanceExceptionEnum.ERR_QR_FORBIDDEN);
        }

        return attendanceRepository.findAllByCourseId(courseId);
    }

    // 출석 현황 조회 (수강생 -> 본인)
    @Transactional(readOnly = true)
    public Attendance findMyAttendance(UUID memberId, UUID courseId) {
        return attendanceRepository.findByMemberIdAndCourseId(memberId, courseId)
                .orElseThrow(() -> new ServiceErrorException(AttendanceExceptionEnum.ERR_NOT_FOUND_ATTENDANCE));
    }

    // SSE 스트림 연결 (강사 전용)
    public SseEmitter stream(UUID courseId, UUID instructorId) {
        // 강사 본인 코스인지 검증
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(CourseExceptionEnum.ERR_NOT_FOUND_COURSE));

        if (!course.getMemberInstructorId().equals(instructorId)) {
            throw new ServiceErrorException(AttendanceExceptionEnum.ERR_QR_FORBIDDEN);
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // 동일한 emitter 인스턴스일 때만 삭제하여 새 연결이 지워지는 것을 방지
        emitter.onCompletion(() -> {
            log.info("SSE 연결 종료 courseId={}", courseId);
            sseEmitterRepository.deleteIfSame(courseId, emitter);
        });
        emitter.onTimeout(() -> {
            log.warn("SSE 연결 타임아웃 courseId={}", courseId);
            sseEmitterRepository.deleteIfSame(courseId, emitter);
            emitter.complete();
        });
        emitter.onError(e -> {
            log.error("SSE 연결 에러 courseId={}", courseId, e);
            sseEmitterRepository.deleteIfSame(courseId, emitter);
        });

        sseEmitterRepository.save(courseId, emitter);

        // 스냅샷 실패 시 예외를 던져서 컨트롤러가 JSON 오류 응답을 내려줄 수 있도록
        try {
            AttendanceListResponse snapshot = attendanceQueryService.getAttendanceSnapshot(courseId);
            emitter.send(SseEmitter.event()
                    .name("snapshot")
                    .data(snapshot));
        } catch (IOException e) {
            log.error("SSE 스냅샷 전송 실패 courseId={}", courseId, e);
            sseEmitterRepository.deleteIfSame(courseId, emitter);
            emitter.completeWithError(e);
            throw new ServiceErrorException(AttendanceExceptionEnum.ERR_NOT_FOUND_ATTENDANCE);
        } catch (Exception e) {
            log.error("SSE 스냅샷 조회 실패 courseId={}", courseId, e);
            sseEmitterRepository.deleteIfSame(courseId, emitter);
            emitter.completeWithError(e);
            throw e;
        }

        return emitter;
    }
}