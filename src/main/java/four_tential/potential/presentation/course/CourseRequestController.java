package four_tential.potential.presentation.course;

import four_tential.potential.application.course.CourseService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.course.model.request.CreateCourseRequestRequest;
import four_tential.potential.presentation.course.model.response.CreateCourseRequestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;

import java.util.UUID;

@Tag(name = "코스 개설 신청", description = "강사의 코스 개설 신청·삭제·재신청 API")
@RestController
@RequestMapping("/v1/course-requests")
@RequiredArgsConstructor
public class CourseRequestController {

    private final CourseService courseService;

    @Operation(summary = "코스 개설 신청", description = "강사가 새로운 코스 개설을 신청합니다. PREPARATION 상태로 생성됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "신청 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "강사 권한 필요")
    })
    @PostMapping
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<BaseResponse<CreateCourseRequestResponse>> createCourseRequest(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody CreateCourseRequestRequest request
    ) {
        CreateCourseRequestResponse response = courseService.createCourseRequest(principal.memberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(HttpStatus.CREATED.name(), "코스 개설 신청 성공", response));
    }

    @Operation(summary = "코스 개설 신청 삭제", description = "PREPARATION 상태의 코스 개설 신청을 삭제합니다. 본인 코스만 삭제 가능합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "400", description = "PREPARATION 상태가 아님"),
            @ApiResponse(responseCode = "403", description = "본인 코스 아님 / 강사 권한 필요"),
            @ApiResponse(responseCode = "404", description = "코스 없음")
    })
    @DeleteMapping("/{courseId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<BaseResponse<Void>> deleteCourseRequest(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable UUID courseId
    ) {
        courseService.deleteCourseRequest(principal.memberId(), courseId);
        return ResponseEntity.ok(BaseResponse.success("OK", "코스 개설 신청 삭제", null));
    }

    @Operation(summary = "코스 개설 재신청", description = "REJECTED 상태의 코스를 다시 PREPARATION 상태로 재신청합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재신청 성공"),
            @ApiResponse(responseCode = "400", description = "REJECTED 상태가 아님"),
            @ApiResponse(responseCode = "403", description = "강사 권한 필요"),
            @ApiResponse(responseCode = "404", description = "코스 없음")
    })
    @PatchMapping("/{courseId}/reapply")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<BaseResponse<Void>> reapplyCourseRequest(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable UUID courseId
    ) {
        courseService.reapplyCourseRequest(principal.memberId(), courseId);
        return ResponseEntity.ok(BaseResponse.success("OK", "코스 개설 재신청 성공", null));
    }
}
