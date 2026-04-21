package four_tential.potential.application.course;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course_category.CourseCategory;
import four_tential.potential.domain.course.course_category.CourseCategoryRepository;
import four_tential.potential.domain.member.instructor_member.InstructorMemberRepository;
import four_tential.potential.presentation.course.model.request.CreateCourseCategoryRequest;
import four_tential.potential.presentation.course.model.request.UpdateCourseCategoryRequest;
import four_tential.potential.presentation.course.model.response.CreateCourseCategoryResponse;
import four_tential.potential.presentation.course.model.response.UpdateCourseCategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.*;

@Service
@RequiredArgsConstructor
public class CourseCategoryService {

    private final CourseCategoryRepository courseCategoryRepository;
    private final CourseRepository courseRepository;
    private final InstructorMemberRepository instructorMemberRepository;

    @Transactional
    public CreateCourseCategoryResponse createCourseCategory(CreateCourseCategoryRequest request) {
        if (courseCategoryRepository.existsByCode(request.code())) {
            throw new ServiceErrorException(ERR_DUPLICATED_CATEGORY_CODE);
        }

        CourseCategory category = CourseCategory.register(request.code(), request.name());
        courseCategoryRepository.save(category);

        return CreateCourseCategoryResponse.register(category);
    }

    @Transactional
    public UpdateCourseCategoryResponse updateCategoryName(String categoryCode, UpdateCourseCategoryRequest request) {
        CourseCategory category = courseCategoryRepository.findByCode(categoryCode)
                .orElseThrow(() -> new ServiceErrorException(ERR_CATEGORY_NOT_FOUND));

        category.updateName(request.name());
        return UpdateCourseCategoryResponse.register(category);
    }

    @Transactional
    public void deleteCategory(String categoryCode) {
        CourseCategory category = courseCategoryRepository.findByCode(categoryCode)
                .orElseThrow(() -> new ServiceErrorException(ERR_CATEGORY_NOT_FOUND));

        if (courseRepository.existsByCourseCategoryId(category.getId())
                || instructorMemberRepository.existsByCategoryCode(categoryCode)) {
            throw new ServiceErrorException(ERR_CATEGORY_HAS_COURSES_OR_INSTRUCTORS);
        }

        courseCategoryRepository.delete(category);
    }
}
