package four_tential.potential.application.attendance;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.AttendanceExceptionEnum;
import four_tential.potential.domain.attendance.Attendance;
import four_tential.potential.domain.attendance.AttendanceRepository;
import four_tential.potential.domain.attendance.AttendanceStatus;
import four_tential.potential.infra.qr.QrCodeGenerator;
import four_tential.potential.infra.redis.qr.QrTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final QrTokenRepository    qrTokenRepository;
    private final QrCodeGenerator      qrCodeGenerator;

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

        // 토큰 유효 확인 (TTL 만료 or 미존재)
        UUID courseId = qrTokenRepository.findCourseIdByToken(qrToken)
                .orElseThrow(() -> new ServiceErrorException(AttendanceExceptionEnum.ERR_QR_NOT_FOUND));

        // 이미 ATTEND 상태면 중복 스캔
        if (attendanceRepository.existsByMemberIdAndCourseIdAndStatus(memberId, courseId, AttendanceStatus.ATTEND)) {
            throw new ServiceErrorException(AttendanceExceptionEnum.ERR_ALREADY_CHECKED);
        }

        // 출석 레코드 조회 후 ATTEND 처리
        Attendance attendance = attendanceRepository.findByMemberIdAndCourseId(memberId, courseId)
                .orElseThrow(() -> new ServiceErrorException(AttendanceExceptionEnum.ERR_NOT_ENROLLED));

        attendance.attend(qrToken);
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
}