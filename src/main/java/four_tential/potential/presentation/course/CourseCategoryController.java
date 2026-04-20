package four_tential.potential.presentation.course;

import four_tential.potential.application.course.CourseCategoryService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.presentation.course.model.request.CreateCourseCategoryRequest;
import four_tential.potential.presentation.course.model.request.UpdateCourseCategoryRequest;
import four_tential.potential.presentation.course.model.response.CreateCourseCategoryResponse;
import four_tential.potential.presentation.course.model.response.UpdateCourseCategoryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin/categories")
@RequiredArgsConstructor
public class CourseCategoryController {

    private final CourseCategoryService courseCategoryService;

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

    @DeleteMapping("/{categoryCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<Void>> deleteCategory(
            @PathVariable String categoryCode
    ) {
        courseCategoryService.deleteCategory(categoryCode);
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "카테고리 삭제 성공", null));
    }
}
