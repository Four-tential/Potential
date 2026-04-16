package four_tential.potential.application.course;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course_category.CourseCategory;
import four_tential.potential.domain.course.course_category.CourseCategoryRepository;
import four_tential.potential.domain.course.fixture.CourseCategoryFixture;
import four_tential.potential.presentation.course.model.request.CreateCourseCategoryRequest;
import four_tential.potential.presentation.course.model.response.CreateCourseCategoryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CourseCategoryServiceTest {

    @Mock
    private CourseCategoryRepository courseCategoryRepository;

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
        assertThat(response.name()).isEqualTo(CourseCategoryFixture.DEFAULT_NAME);
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
}
