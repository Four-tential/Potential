package four_tential.potential.presentation.attendance;

import four_tential.potential.application.attendance.AttendanceService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.AttendanceExceptionEnum;
import four_tential.potential.domain.attendance.Attendance;
import four_tential.potential.presentation.attendance.dto.AttendanceListResponse;
import four_tential.potential.presentation.attendance.dto.AttendanceScanRequest;
import four_tential.potential.domain.member.member.MemberRole;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    // QR 생성(강사) - PNG 파일 형식이라 공통 응답으로 감쌀 수 없음
    @PostMapping(
            value = "/courses/{courseId}/attendances/qr",
            produces = MediaType.IMAGE_PNG_VALUE
    )
    public ResponseEntity<byte[]> createQr(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        // 강사 권한 검증
        if (!MemberRole.ROLE_INSTRUCTOR.name().equals(principal.role())) {
            throw new ServiceErrorException(AttendanceExceptionEnum.ERR_QR_FORBIDDEN);
        }

        byte[] qrImage = attendanceService.createQr(courseId, principal.memberId());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(qrImage);
    }

    // QR 스캔 출석 처리(수강생)
    @PostMapping("/attendances/scan")
    public ResponseEntity<BaseResponse<Void>> scan(
            @RequestBody @Valid AttendanceScanRequest request,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        if (!MemberRole.ROLE_STUDENT.name().equals(principal.role())) {
            throw new ServiceErrorException(AttendanceExceptionEnum.ERR_SCAN_ONLY_STUDENT);
        }
        attendanceService.scan(request.getQrToken(), principal.memberId());
        return ResponseEntity.ok(
                BaseResponse.success(HttpStatus.OK.name(), "출석 처리가 완료되었습니다", null)
        );
    }

    // 출석 현황 조회 — 강사: 전체 / 수강생: 본인
    @GetMapping("/courses/{courseId}/attendances")
    public ResponseEntity<BaseResponse<AttendanceListResponse>> getAttendances(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        AttendanceListResponse response;

        if (MemberRole.ROLE_INSTRUCTOR.name().equals(principal.role())) {
            List<Attendance> attendances = attendanceService.findAllByCourse(courseId);
            response = AttendanceListResponse.ofInstructor(attendances);
        } else {
            Attendance attendance = attendanceService.findMyAttendance(principal.memberId(), courseId);
            response = AttendanceListResponse.ofStudent(attendance);
        }

        return ResponseEntity.ok(
                BaseResponse.success(HttpStatus.OK.name(), "출석 현황 조회가 완료되었습니다", response)
        );
    }

    // 실시간 출석 현황 스트림 (강사 전용 SSE)
    // 추후 수강생 스트림 등 확장 시 엔드포인트 추가
    @GetMapping(
            value = "/courses/{courseId}/attendances/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public ResponseEntity<?> stream(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        // 연결 전 토큰 검증
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BaseResponse.fail(HttpStatus.UNAUTHORIZED.name(), "유효하지 않은 토큰입니다"));
        }

        //권한 검증(강사)
        if (!MemberRole.ROLE_INSTRUCTOR.name().equals(principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BaseResponse.fail(HttpStatus.FORBIDDEN.name(),
                            AttendanceExceptionEnum.ERR_ATTENDANCE_FORBIDDEN.getMessage()));
        }

        // SSE 접속 시 따로 GlobalExceptionHandler에서 JSON 응답을 내려줄 수 없으므로
        // 연결 전 발생하는 예외는 컨트롤러에서 직접 처리
        try {
            //SSE 연결
            SseEmitter emitter = attendanceService.stream(courseId, principal.memberId());
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(emitter);
        } catch (ServiceErrorException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BaseResponse.fail(e.getHttpStatus().name(), e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BaseResponse.fail(HttpStatus.INTERNAL_SERVER_ERROR.name(),
                            "스트림 연결에 실패했습니다"));
        }
    }
}