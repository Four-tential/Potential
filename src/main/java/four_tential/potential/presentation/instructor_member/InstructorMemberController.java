package four_tential.potential.presentation.instructor_member;

import four_tential.potential.application.instructor_member.InstructorMemberService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.member.instructor_member.InstructorMemberStatus;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.instructor_member.model.request.ApplyInstructorRequest;
import four_tential.potential.presentation.instructor_member.model.request.InstructorActionRequest;
import four_tential.potential.presentation.instructor_member.model.response.InstructorActionResponse;
import four_tential.potential.presentation.instructor_member.model.response.ApplyInstructorResponse;
import four_tential.potential.presentation.instructor_member.model.response.InstructorApplicationDetail;
import four_tential.potential.presentation.instructor_member.model.response.InstructorApplicationItem;
import four_tential.potential.presentation.instructor_member.model.response.MyInstructorApplicationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_NOT_AUTHORIZE_TO_INSTRUCTOR;

@Tag(name = "강사 전환 신청", description = "강사 전환 신청·이력 조회·어드민 승인·반려 API")
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class InstructorMemberController {

    private final InstructorMemberService instructorMemberService;

    @Operation(summary = "강사 전환 신청", description = "STUDENT 회원이 강사 전환을 신청합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "신청 성공"),
            @ApiResponse(responseCode = "400", description = "이미 강사이거나 신청 불가 상태"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping("/members/me/instructor-applications")
    public ResponseEntity<BaseResponse<ApplyInstructorResponse>> applyInstructor(
            @Valid @RequestBody ApplyInstructorRequest request,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        if (!principal.role().equals("ROLE_STUDENT")) {
            throw new ServiceErrorException(ERR_NOT_AUTHORIZE_TO_INSTRUCTOR);
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(
                        HttpStatus.CREATED.name(),
                        "강사 전환 신청 성공",
                        instructorMemberService.applyInstructor(principal.memberId(), request)
                ));
    }

    @Operation(summary = "내 강사 신청 이력 조회", description = "로그인한 회원의 강사 전환 신청 이력을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/members/me/instructor-applications")
    public ResponseEntity<BaseResponse<MyInstructorApplicationResponse>> getMyInstructorApplication(
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        return ResponseEntity.ok(BaseResponse.success(
                HttpStatus.OK.name(),
                "내 강사 신청 이력 조회 성공",
                instructorMemberService.getMyInstructorApplication(principal.memberId())
        ));
    }

    @Operation(summary = "강사 신청 목록 조회 (어드민)", description = "관리자가 전체 강사 전환 신청 목록을 상태 필터로 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "어드민 권한 필요")
    })
    @GetMapping("/admin/instructor-applications")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<PageResponse<InstructorApplicationItem>>> getInstructorApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) InstructorMemberStatus status
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(
                        HttpStatus.OK.name(),
                        "강사 신청 목록 조회 성공",
                        instructorMemberService.getInstructorApplications(status, PageRequest.of(page, size))
                ));
    }

    @Operation(summary = "강사 신청 승인/반려 (어드민)", description = "관리자가 강사 전환 신청을 승인 또는 반려합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공"),
            @ApiResponse(responseCode = "400", description = "처리 불가 상태"),
            @ApiResponse(responseCode = "403", description = "어드민 권한 필요"),
            @ApiResponse(responseCode = "404", description = "신청 없음")
    })
    @PatchMapping("/admin/instructor-applications/{memberId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<InstructorActionResponse>> processInstructorApplication(
            @PathVariable UUID memberId,
            @Valid @RequestBody InstructorActionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(
                        HttpStatus.OK.name(),
                        request.action().name().equals("APPROVE") ? "강사 신청 승인" : "강사 신청 반려",
                        instructorMemberService.processInstructorApplication(memberId, request)
                ));
    }

    @Operation(summary = "강사 신청 상세 조회 (어드민)", description = "관리자가 특정 회원의 강사 전환 신청 상세 내용을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "어드민 권한 필요"),
            @ApiResponse(responseCode = "404", description = "신청 없음")
    })
    @GetMapping("/admin/instructor-applications/{memberId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<InstructorApplicationDetail>> getInstructorApplicationDetail(
            @PathVariable UUID memberId
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(BaseResponse.success(
                        HttpStatus.OK.name(),
                        "강사 신청 상세 조회 성공",
                        instructorMemberService.getInstructorApplicationDetail(memberId)
                ));
    }
}
