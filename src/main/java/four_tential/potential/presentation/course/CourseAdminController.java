package four_tential.potential.presentation.course;

import four_tential.potential.application.course.CourseService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.domain.course.course_approval_history.CourseApprovalAction;
import four_tential.potential.presentation.course.model.request.CourseRequestActionRequest;
import four_tential.potential.presentation.course.model.response.CourseRequestActionResponse;
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

@RestController
@RequestMapping("/v1/admin/course-requests")
@RequiredArgsConstructor
public class CourseAdminController {

    private final CourseService courseService;

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
