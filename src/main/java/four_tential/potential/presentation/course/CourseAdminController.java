package four_tential.potential.presentation.course;

import four_tential.potential.application.course.CourseService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.domain.course.course_approval_history.CourseApprovalAction;
import four_tential.potential.presentation.course.model.request.CourseRequestActionRequest;
import four_tential.potential.presentation.course.model.response.CourseRequestActionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "코스 관리 (어드민)", description = "관리자의 코스 개설 신청 승인·반려 API")
@RestController
@RequestMapping("/v1/admin/course-requests")
@RequiredArgsConstructor
public class CourseAdminController {

    private final CourseService courseService;

    @Operation(summary = "코스 개설 신청 승인/반려", description = "PREPARATION 상태의 코스를 승인(OPEN) 또는 반려(REJECTED)합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공"),
            @ApiResponse(responseCode = "400", description = "PREPARATION 상태가 아님 / 유효하지 않은 action"),
            @ApiResponse(responseCode = "403", description = "어드민 권한 필요"),
            @ApiResponse(responseCode = "404", description = "코스 없음")
    })
    @PatchMapping("/{courseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<CourseRequestActionResponse>> handleCourseRequest(
            @PathVariable UUID courseId,
            @Valid @RequestBody CourseRequestActionRequest request
    ) {
        CourseRequestActionResponse response = courseService.handleCourseRequest(courseId, request);
        String message = request.action() == CourseApprovalAction.APPROVE ? "코스 승인 성공" : "코스 반려 성공";
        return ResponseEntity.ok(BaseResponse.success("OK", message, response));
    }
}
