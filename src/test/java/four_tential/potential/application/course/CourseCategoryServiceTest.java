package four_tential.potential.application.course;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course_category.CourseCategory;
import four_tential.potential.domain.course.course_category.CourseCategoryRepository;
import four_tential.potential.domain.course.fixture.CourseCategoryFixture;
import four_tential.potential.domain.member.instructor_member.InstructorMemberRepository;
import four_tential.potential.presentation.course.model.request.CreateCourseCategoryRequest;
import four_tential.potential.presentation.course.model.request.UpdateCourseCategoryRequest;
import four_tential.potential.presentation.course.model.response.CreateCourseCategoryResponse;
import four_tential.potential.presentation.course.model.response.UpdateCourseCategoryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CourseCategoryServiceTest {

    @Mock
    private CourseCategoryRepository courseCategoryRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private InstructorMemberRepository instructorMemberRepository;

    @InjectMocks
    private CourseCategoryService courseCategoryService;

    // region createCourseCategory
    @Test
    @DisplayName("카테고리 생성 성공 - 저장 후 code, name 반환")
    void createCourseCategory_success() {
        CreateCourseCategoryRequest request = new CreateCourseCategoryRequest("DANCE", "댄스");
        CourseCategory category = CourseCategoryFixture.courseCategoryWithCode("DANCE");

        given(courseCategoryRepository.existsByCode("DANCE")).willReturn(false);
        given(courseCategoryRepository.save(any())).willReturn(category);

        CreateCourseCategoryResponse response = courseCategoryService.createCourseCategory(request);

        assertThat(response.code()).isEqualTo("DANCE");
        assertThat(response.name()).isEqualTo("댄스");
        verify(courseCategoryRepository).save(any(CourseCategory.class));
    }

    @Test
    @DisplayName("카테고리 생성 - 중복 코드이면 ServiceErrorException 발생")
    void createCourseCategory_duplicatedCode() {
        CreateCourseCategoryRequest request = new CreateCourseCategoryRequest(
                CourseCategoryFixture.DEFAULT_CODE, CourseCategoryFixture.DEFAULT_NAME
        );
        given(courseCategoryRepository.existsByCode(CourseCategoryFixture.DEFAULT_CODE)).willReturn(true);

        assertThatThrownBy(() -> courseCategoryService.createCourseCategory(request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("이미 존재하는 카테고리 코드입니다");
    }
    // endregion

    // region updateCategoryName
    @Test
    @DisplayName("카테고리 이름 수정 성공 - 수정된 code, name, updatedAt 반환")
    void updateCategoryName_success() {
        CourseCategory category = CourseCategoryFixture.defaultCourseCategory();
        UpdateCourseCategoryRequest request = new UpdateCourseCategoryRequest("댄스/무용");

        given(courseCategoryRepository.findByCode(CourseCategoryFixture.DEFAULT_CODE))
                .willReturn(Optional.of(category));

        UpdateCourseCategoryResponse response =
                courseCategoryService.updateCategoryName(CourseCategoryFixture.DEFAULT_CODE, request);

        assertThat(response.code()).isEqualTo(CourseCategoryFixture.DEFAULT_CODE);
        assertThat(response.name()).isEqualTo("댄스/무용");
    }

    @Test
    @DisplayName("카테고리 이름 수정 실패 - 존재하지 않는 코드이면 ERR_CATEGORY_NOT_FOUND")
    void updateCategoryName_notFound() {
        given(courseCategoryRepository.findByCode("UNKNOWN")).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                courseCategoryService.updateCategoryName("UNKNOWN", new UpdateCourseCategoryRequest("이름"))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 카테고리입니다");
    }
    // endregion

    // region deleteCategory
    @Test
    @DisplayName("카테고리 삭제 성공 - 코스와 강사가 없으면 삭제")
    void deleteCategory_success() {
        CourseCategory category = CourseCategoryFixture.defaultCourseCategory();

        given(courseCategoryRepository.findByCode(CourseCategoryFixture.DEFAULT_CODE))
                .willReturn(Optional.of(category));
        given(courseRepository.existsByCourseCategoryId(category.getId())).willReturn(false);
        given(instructorMemberRepository.existsByCategoryCode(CourseCategoryFixture.DEFAULT_CODE))
                .willReturn(false);

        courseCategoryService.deleteCategory(CourseCategoryFixture.DEFAULT_CODE);

        verify(courseCategoryRepository).delete(category);
    }

    @Test
    @DisplayName("카테고리 삭제 실패 - 존재하지 않는 코드이면 ERR_CATEGORY_NOT_FOUND")
    void deleteCategory_notFound() {
        given(courseCategoryRepository.findByCode("UNKNOWN")).willReturn(Optional.empty());

        assertThatThrownBy(() -> courseCategoryService.deleteCategory("UNKNOWN"))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 카테고리입니다");

        verify(courseCategoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("카테고리 삭제 실패 - 카테고리 내 코스가 존재하면 ERR_CATEGORY_HAS_COURSES_OR_INSTRUCTORS")
    void deleteCategory_hasCourses() {
        CourseCategory category = CourseCategoryFixture.defaultCourseCategory();

        given(courseCategoryRepository.findByCode(CourseCategoryFixture.DEFAULT_CODE))
                .willReturn(Optional.of(category));
        given(courseRepository.existsByCourseCategoryId(category.getId())).willReturn(true);

        assertThatThrownBy(() -> courseCategoryService.deleteCategory(CourseCategoryFixture.DEFAULT_CODE))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("카테고리 내 코스 또는 강사가 존재하여 삭제할 수 없습니다");

        verify(courseCategoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("카테고리 삭제 실패 - 카테고리 내 강사가 존재하면 ERR_CATEGORY_HAS_COURSES_OR_INSTRUCTORS")
    void deleteCategory_hasInstructors() {
        CourseCategory category = CourseCategoryFixture.defaultCourseCategory();

        given(courseCategoryRepository.findByCode(CourseCategoryFixture.DEFAULT_CODE))
                .willReturn(Optional.of(category));
        given(courseRepository.existsByCourseCategoryId(category.getId())).willReturn(false);
        given(instructorMemberRepository.existsByCategoryCode(CourseCategoryFixture.DEFAULT_CODE))
                .willReturn(true);

        assertThatThrownBy(() -> courseCategoryService.deleteCategory(CourseCategoryFixture.DEFAULT_CODE))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("카테고리 내 코스 또는 강사가 존재하여 삭제할 수 없습니다");

        verify(courseCategoryRepository, never()).delete(any());
    }
    // endregion
}
