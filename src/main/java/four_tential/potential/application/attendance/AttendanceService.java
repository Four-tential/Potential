package four_tential.potential.application.attendance;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.AttendanceExceptionEnum;
import four_tential.potential.domain.attendance.Attendance;
import four_tential.potential.domain.attendance.AttendanceRepository;
import four_tential.potential.domain.attendance.AttendanceStatus;
import four_tential.potential.domain.attendance.dto.AttendanceEventResponse;
import four_tential.potential.domain.attendance.dto.AttendanceListResponse;
import four_tential.potential.infra.qr.QrCodeGenerator;
import four_tential.potential.infra.qr.QrTokenRepository;
import four_tential.potential.infra.sse.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30분

    // QR 생성(강사 전용)
    public byte[] createQr(UUID courseId, UUID instructorId) {
        /*TODO: 코스, 유저 도메인 추가 시 추가할 검증
            1. 강사 본인 코스인 지
            2. 코스 시작 후 10분 이내 인 지
        */

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

        Attendance attendance = attendanceRepository.findByMemberIdAndCourseId(memberId, courseId)
                .orElseThrow(() -> new ServiceErrorException(AttendanceExceptionEnum.ERR_NOT_ENROLLED));

        attendance.attend(qrToken);

        // DB 커밋 성공 후에만 SSE 이벤트 전송 - 커밋 전 전송 시 DB 롤백과 화면 불일치 방지
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        pushAttendanceEvent(courseId, attendance);
                    }
                }
        );
    }

    // 출석 현황 조회 (강사 -> 전체)
    @Transactional(readOnly = true)
    public List<Attendance> findAllByCourse(UUID courseId) {
        return attendanceRepository.findAllByCourseId(courseId);
    }

    // 출석 현황 조회 (수강생 -> 본인)
    @Transactional(readOnly = true)
    public Attendance findMyAttendance(UUID memberId, UUID courseId) {
        return attendanceRepository.findByMemberIdAndCourseId(memberId, courseId)
                .orElseThrow(() -> new ServiceErrorException(AttendanceExceptionEnum.ERR_NOT_FOUND_ATTENDANCE));
    }

    // SSE 스트림 연결 (강사 전용)
    // 추후 수강생 등 다른 역할로 확장 시 파라미터에 role 추가하여 분기 처리 가능
    public SseEmitter stream(UUID courseId, UUID instructorId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onCompletion(() -> {
            log.info("SSE 연결 종료 courseId={}", courseId);
            sseEmitterRepository.delete(courseId);
        });
        emitter.onTimeout(() -> {
            log.warn("SSE 연결 타임아웃 courseId={}", courseId);
            sseEmitterRepository.delete(courseId);
            emitter.complete();
        });
        emitter.onError(e -> {
            log.error("SSE 연결 에러 courseId={}", courseId, e);
            sseEmitterRepository.delete(courseId);
        });

        sseEmitterRepository.save(courseId, emitter);

        try {
            AttendanceListResponse snapshot = attendanceQueryService.getAttendanceSnapshot(courseId);

            emitter.send(SseEmitter.event()
                    .name("snapshot")
                    .data(snapshot));

        } catch (IOException e) {
            log.error("SSE 스냅샷 전송 실패 courseId={}", courseId, e);
            sseEmitterRepository.delete(courseId);
            emitter.completeWithError(e);
        } catch (Exception e) {
            log.error("SSE 스냅샷 조회 실패 courseId={}", courseId, e);
            sseEmitterRepository.delete(courseId);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    // SSE 이벤트 푸시
    private void pushAttendanceEvent(UUID courseId, Attendance attendance) {
        sseEmitterRepository.findByCourseId(courseId).ifPresent(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("attendance")
                        .data(AttendanceEventResponse.from(attendance)));

            } catch (IOException e) {
                // 클라이언트 연결 끊김
                log.warn("SSE 클라이언트 연결 끊김 courseId={} memberId={}",
                        courseId, attendance.getMemberId());
                sseEmitterRepository.delete(courseId);
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE 이벤트 전송 실패 courseId={}", courseId, e);
                sseEmitterRepository.delete(courseId);
                emitter.completeWithError(e);
            }
        });
    }
}