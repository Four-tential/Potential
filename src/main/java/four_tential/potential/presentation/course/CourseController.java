package four_tential.potential.presentation.course;

import four_tential.potential.application.course.CourseService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseSearchCondition;
import four_tential.potential.domain.course.course.CourseSort;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import four_tential.potential.presentation.course.model.request.UpdateCourseRequest;
import four_tential.potential.presentation.course.model.response.CourseDetailResponse;
import four_tential.potential.presentation.course.model.response.CourseListItem;
import four_tential.potential.presentation.course.model.response.CourseWishlistResponse;
import four_tential.potential.presentation.course.model.response.UpdateCourseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.util.UUID;

@Tag(name = "코스", description = "코스 조회·수정·찜·종료 API")
@RestController
@RequestMapping("/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @Operation(summary = "코스 목록 조회", description = "조건·정렬 기반 코스 목록을 페이지로 조회합니다. 비인증 사용자도 조회 가능합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
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

    @Operation(summary = "코스 수정", description = "PREPARATION 상태 코스의 정보를 수정합니다. 본인 코스만 수정 가능합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "OPEN 상태에서 수정 불가 필드 포함 / CLOSED·CANCELLED 코스"),
            @ApiResponse(responseCode = "403", description = "본인 코스 아님"),
            @ApiResponse(responseCode = "404", description = "코스 없음")
    })
    @PatchMapping("/{courseId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<BaseResponse<UpdateCourseResponse>> updateCourse(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable UUID courseId,
            @Valid @RequestBody UpdateCourseRequest request
    ) {
        UpdateCourseResponse response = courseService.updateCourse(principal.memberId(), courseId, request);
        return ResponseEntity.ok(BaseResponse.success("OK", "코스가 수정되었습니다", response));
    }

    @Operation(summary = "코스 찜 추가", description = "코스를 찜 목록에 추가합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "추가 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "코스 없음"),
            @ApiResponse(responseCode = "409", description = "이미 찜한 코스")
    })
    @PostMapping("/{courseId}/wishlist-courses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<CourseWishlistResponse>> addWishlist(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable UUID courseId
    ) {
        CourseWishlistResponse response = courseService.addWishlist(principal.memberId(), courseId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(HttpStatus.CREATED.name(), "찜 목록에 추가 성공", response));
    }

    @Operation(summary = "코스 찜 해제", description = "코스를 찜 목록에서 제거합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "해제 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "찜 기록 없음")
    })
    @DeleteMapping("/{courseId}/wishlist-courses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<CourseWishlistResponse>> removeWishlist(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable UUID courseId
    ) {
        CourseWishlistResponse response = courseService.removeWishlist(principal.memberId(), courseId);
        return ResponseEntity.ok(BaseResponse.success("OK", "찜 목록에서 제거 성공", response));
    }

    @Operation(summary = "코스 종료", description = "OPEN 상태의 코스를 CLOSED로 전환합니다. 본인 코스만 종료 가능합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "종료 성공"),
            @ApiResponse(responseCode = "400", description = "OPEN 상태가 아님"),
            @ApiResponse(responseCode = "403", description = "본인 코스 아님"),
            @ApiResponse(responseCode = "404", description = "코스 없음")
    })
    @PatchMapping("/{courseId}/close")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<BaseResponse<Void>> closeCourse(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable UUID courseId
    ) {
        courseService.closeCourse(principal.memberId(), courseId);
        return ResponseEntity.ok(BaseResponse.success("OK", "코스 종료 성공", null));
    }

    @Operation(summary = "코스 상세 조회", description = "코스 상세 정보를 조회합니다. 비인증 사용자도 조회 가능합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "코스 없음")
    })
    @GetMapping("/{courseId}")
    public ResponseEntity<BaseResponse<CourseDetailResponse>> getCourseDetail(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable UUID courseId
    ) {
        UUID memberId = principal != null ? principal.memberId() : null;

        CourseDetailResponse response = courseService.getCourseDetail(courseId, memberId);

        return ResponseEntity.ok(BaseResponse.success("OK", "코스 상세 조회 성공", response));
    }
}
