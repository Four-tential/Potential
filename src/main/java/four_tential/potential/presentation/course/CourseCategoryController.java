package four_tential.potential.presentation.course;

import four_tential.potential.application.course.CourseCategoryService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.presentation.course.model.request.CreateCourseCategoryRequest;
import four_tential.potential.presentation.course.model.response.CreateCourseCategoryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/course-categories")
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
}
