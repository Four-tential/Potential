package four_tential.potential.presentation.course;

import four_tential.potential.application.course.CourseService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseSearchCondition;
import four_tential.potential.domain.course.course.CourseSort;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.course.model.response.CourseDetailResponse;
import four_tential.potential.presentation.course.model.response.CourseListItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.util.UUID;

@RestController
@RequestMapping("/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<CourseListItem>>> getCourses(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) CourseStatus status,
            @RequestParam(required = false) CourseLevel level,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigInteger minPrice,
            @RequestParam(required = false) BigInteger maxPrice,
            @RequestParam(required = false) CourseSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        CourseSearchCondition condition = new CourseSearchCondition(categoryCode, status, level, keyword, minPrice, maxPrice, sort);
        Pageable pageable = PageRequest.of(page, size);
        UUID memberId = principal != null ? principal.memberId() : null;

        PageResponse<CourseListItem> response = courseService.getCourses(condition, memberId, pageable);

        return ResponseEntity.ok(BaseResponse.success("OK", "코스 목록 조회 성공", response));
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<BaseResponse<CourseDetailResponse>> getCourseDetail(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable UUID courseId
    ) {
        UUID memberId = principal != null ? principal.memberId() : null;

        CourseDetailResponse response = courseService.getCourseDetail(courseId, memberId);

        return ResponseEntity.ok(BaseResponse.success("OK", "코스 상세 조회가 완료되었습니다.", response));
    }
}
