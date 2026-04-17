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

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class InstructorMemberController {

    private final InstructorMemberService instructorMemberService;

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
