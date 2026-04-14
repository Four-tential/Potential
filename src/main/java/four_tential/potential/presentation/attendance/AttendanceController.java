package four_tential.potential.presentation.attendance;

import four_tential.potential.application.attendance.AttendanceService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.domain.attendance.Attendance;
import four_tential.potential.domain.attendance.dto.AttendanceListResponse;
import four_tential.potential.domain.attendance.dto.AttendanceScanRequest;
import four_tential.potential.domain.member.member.MemberRole;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
}