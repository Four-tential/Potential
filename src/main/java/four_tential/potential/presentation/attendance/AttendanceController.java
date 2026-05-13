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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@Tag(name = "출석", description = "QR 코드 생성 · 스캔 출석 처리 · 출석 현황 조회 · 실시간 SSE 스트림 API")
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @Operation(
            summary = "QR 코드 생성 (강사 전용)",
            description = """
                    강사가 클래스 출석용 QR 코드를 생성합니다.
                    
                    - 본인 소유 클래스에 한해 생성 가능합니다.
                    - 클래스 시작 시각으로부터 10분 이내에만 생성할 수 있습니다.
                    - Redis SETNX를 사용해 클래스당 QR 중복 생성을 원자적으로 차단합니다.
                    - QR 토큰은 TTL 600초(10분) 후 자동 만료됩니다.
                    - 응답은 PNG 이미지(400×400)로 반환됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "QR 이미지 반환 (image/png)"),
            @ApiResponse(responseCode = "403", description = "강사 권한 없음 또는 본인 클래스가 아님"),
            @ApiResponse(responseCode = "409", description = "이미 QR이 발급된 클래스"),
            @ApiResponse(responseCode = "400", description = "클래스 시작 10분 이후 요청")
    })
    @PostMapping(
            value = "/courses/{courseId}/attendances/qr",
            produces = MediaType.IMAGE_PNG_VALUE
    )
    public ResponseEntity<byte[]> createQr(
            @Parameter(description = "QR을 생성할 클래스 ID", required = true)
            @PathVariable UUID courseId,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        if (!MemberRole.ROLE_INSTRUCTOR.name().equals(principal.role())) {
            throw new ServiceErrorException(AttendanceExceptionEnum.ERR_QR_FORBIDDEN);
        }

        byte[] qrImage = attendanceService.createQr(courseId, principal.memberId());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(qrImage);
    }

    @Operation(
            summary = "QR 스캔 출석 처리 (수강생 전용)",
            description = """
                    수강생이 QR 코드를 스캔해 출석을 처리합니다.
                    
                    - 수강생 권한만 호출 가능합니다.
                    - QR 토큰을 Redis에서 역조회해 클래스 ID를 확인합니다.
                    - 해당 클래스의 결제 상태가 CONFIRMED인 경우에만 출석 처리됩니다.
                    - Pessimistic Lock(FOR UPDATE)으로 동시 중복 출석을 방지합니다.
                    - 출석 완료 후 SSE로 강사 화면에 실시간 반영됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "출석 처리 완료"),
            @ApiResponse(responseCode = "403", description = "수강생 권한 없음"),
            @ApiResponse(responseCode = "404", description = "유효하지 않거나 만료된 QR 토큰"),
            @ApiResponse(responseCode = "409", description = "이미 출석 처리된 수강생")
    })
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

    @Operation(
            summary = "출석 현황 조회",
            description = """
                    클래스의 출석 현황을 조회합니다. 역할에 따라 반환 범위가 다릅니다.
                    
                    - **강사**: 해당 클래스 전체 수강생의 출석 목록 반환 (본인 클래스만 조회 가능)
                    - **수강생**: 본인의 출석 상태만 반환
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "출석 현황 조회 완료"),
            @ApiResponse(responseCode = "403", description = "본인 클래스가 아님 (강사) 또는 미수강 클래스 (수강생)"),
            @ApiResponse(responseCode = "404", description = "클래스를 찾을 수 없음")
    })
    @GetMapping("/courses/{courseId}/attendances")
    public ResponseEntity<BaseResponse<AttendanceListResponse>> getAttendances(
            @Parameter(description = "조회할 클래스 ID", required = true)
            @PathVariable UUID courseId,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        AttendanceListResponse response;

        if (MemberRole.ROLE_INSTRUCTOR.name().equals(principal.role())) {
            response = attendanceService.findAllByCourse(courseId, principal.memberId());
        } else {
            Attendance attendance = attendanceService.findMyAttendance(principal.memberId(), courseId);
            response = AttendanceListResponse.ofStudent(attendance);
        }

        return ResponseEntity.ok(
                BaseResponse.success(HttpStatus.OK.name(), "출석 현황 조회가 완료되었습니다", response)
        );
    }

    @Operation(
            summary = "실시간 출석 현황 스트림 (강사 전용 SSE)",
            description = """
                    강사가 출석 화면을 열면 SSE 연결을 맺고, 수강생이 QR을 스캔할 때마다 실시간으로 출석 이벤트를 수신합니다.
                    
                    - 강사 권한만 연결 가능합니다.
                    - 연결 시 현재 출석 스냅샷을 즉시 전송합니다.
                    - 수강생 출석 처리 트랜잭션 커밋 이후 이벤트가 발행됩니다 (데이터 정합성 보장).
                    - SSE 특성상 Swagger UI에서 직접 테스트가 어렵습니다. curl 또는 브라우저 EventSource로 확인하세요.
                    
                    ```
                    curl -N -H "Authorization: Bearer {token}" \\
                      http://localhost:8080/v1/courses/{courseId}/attendances/stream
                    ```
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE 스트림 연결 성공 (text/event-stream)"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 토큰"),
            @ApiResponse(responseCode = "403", description = "강사 권한 없음 또는 본인 클래스가 아님")
    })
    @GetMapping(
            value = "/courses/{courseId}/attendances/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public ResponseEntity<?> stream(
            @Parameter(description = "스트림을 연결할 클래스 ID", required = true)
            @PathVariable UUID courseId,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BaseResponse.fail(HttpStatus.UNAUTHORIZED.name(), "유효하지 않은 토큰입니다"));
        }

        if (!MemberRole.ROLE_INSTRUCTOR.name().equals(principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BaseResponse.fail(HttpStatus.FORBIDDEN.name(),
                            AttendanceExceptionEnum.ERR_ATTENDANCE_FORBIDDEN.getMessage()));
        }

        try {
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