package four_tential.potential.presentation.course;

import four_tential.potential.application.course.CourseCategoryService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.presentation.course.model.request.CreateCourseCategoryRequest;
import four_tential.potential.presentation.course.model.request.UpdateCourseCategoryRequest;
import four_tential.potential.presentation.course.model.response.CreateCourseCategoryResponse;
import four_tential.potential.presentation.course.model.response.UpdateCourseCategoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "코스 카테고리 (어드민)", description = "관리자의 코스 카테고리 생성·수정·삭제 API")
@RestController
@RequestMapping("/v1/admin/categories")
@RequiredArgsConstructor
public class CourseCategoryController {

    private final CourseCategoryService courseCategoryService;

    @Operation(summary = "카테고리 생성", description = "새로운 코스 카테고리를 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패"),
            @ApiResponse(responseCode = "403", description = "어드민 권한 필요")
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<CreateCourseCategoryResponse>> createCourseCategory(
            @Valid @RequestBody CreateCourseCategoryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(
                        HttpStatus.CREATED.name(),
                        "카테고리 생성 성공",
                        courseCategoryService.createCourseCategory(request)
                ));
    }

    @Operation(summary = "카테고리 이름 수정", description = "카테고리 코드로 카테고리 이름을 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패"),
            @ApiResponse(responseCode = "403", description = "어드민 권한 필요"),
            @ApiResponse(responseCode = "404", description = "카테고리 없음")
    })
    @PatchMapping("/{categoryCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<UpdateCourseCategoryResponse>> updateCategoryName(
            @PathVariable String categoryCode,
            @Valid @RequestBody UpdateCourseCategoryRequest request
    ) {
        return ResponseEntity.ok(BaseResponse.success(
                HttpStatus.OK.name(),
                "카테고리 수정 성공",
                courseCategoryService.updateCategoryName(categoryCode, request)
        ));
    }

    @Operation(summary = "카테고리 삭제", description = "카테고리 코드로 카테고리를 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "403", description = "어드민 권한 필요"),
            @ApiResponse(responseCode = "404", description = "카테고리 없음")
    })
    @DeleteMapping("/{categoryCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<Void>> deleteCategory(
            @PathVariable String categoryCode
    ) {
        courseCategoryService.deleteCategory(categoryCode);
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "카테고리 삭제 성공", null));
    }
}
