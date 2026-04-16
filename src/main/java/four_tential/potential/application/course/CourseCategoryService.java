package four_tential.potential.application.course;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course_category.CourseCategory;
import four_tential.potential.domain.course.course_category.CourseCategoryRepository;
import four_tential.potential.presentation.course.model.request.CreateCourseCategoryRequest;
import four_tential.potential.presentation.course.model.response.CreateCourseCategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_DUPLICATED_CATEGORY_CODE;

@Service
@RequiredArgsConstructor
public class CourseCategoryService {

    private final CourseCategoryRepository courseCategoryRepository;

    @Transactional
    public CreateCourseCategoryResponse createCourseCategory(CreateCourseCategoryRequest request) {
        if (courseCategoryRepository.existsByCode(request.code())) {
            throw new ServiceErrorException(ERR_DUPLICATED_CATEGORY_CODE);
        }

        CourseCategory category = CourseCategory.register(request.code(), request.name());
        courseCategoryRepository.save(category);

        return CreateCourseCategoryResponse.register(category);
    }
}
