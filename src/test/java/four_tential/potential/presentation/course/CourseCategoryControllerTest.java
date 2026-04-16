package four_tential.potential.presentation.course;

import four_tential.potential.application.course.CourseCategoryService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.course.model.request.CreateCourseCategoryRequest;
import four_tential.potential.presentation.course.model.response.CreateCourseCategoryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_DUPLICATED_CATEGORY_CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CourseCategoryControllerTest {

    @Mock
    private CourseCategoryService courseCategoryService;

    @InjectMocks
    private CourseCategoryController courseCategoryController;

    private static final MemberPrincipal ADMIN_PRINCIPAL =
            new MemberPrincipal(UUID.randomUUID(), "admin@example.com", "ROLE_ADMIN");
    private static final MemberPrincipal STUDENT_PRINCIPAL =
            new MemberPrincipal(UUID.randomUUID(), "student@example.com", "ROLE_STUDENT");

    // region createCourseCategory
    @Test
    @DisplayName("카테고리 생성 - ADMIN 이면 201 CREATED 및 생성 정보 반환")
    void createCourseCategory_success() {
        CreateCourseCategoryRequest request = new CreateCourseCategoryRequest("DANCE", "댄스");
        CreateCourseCategoryResponse serviceResponse = new CreateCourseCategoryResponse(
                "DANCE", "댄스", LocalDateTime.now()
        );
        given(courseCategoryService.createCourseCategory(request)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<CreateCourseCategoryResponse>> response =
                courseCategoryController.createCourseCategory(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().code()).isEqualTo("DANCE");
        assertThat(response.getBody().data().name()).isEqualTo("댄스");
    }

    @Test
    @DisplayName("카테고리 생성 - ADMIN 이 아니면 ServiceErrorException 발생")
    void createCourseCategory_notAdmin() {
        CreateCourseCategoryRequest request = new CreateCourseCategoryRequest("DANCE", "댄스");

        assertThatThrownBy(() -> courseCategoryController.createCourseCategory(request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("관리자만 접근 할 수 있습니다");
    }

    @Test
    @DisplayName("카테고리 생성 - 중복 코드이면 ServiceErrorException 전파")
    void createCourseCategory_duplicatedCode() {
        CreateCourseCategoryRequest request = new CreateCourseCategoryRequest("DANCE", "댄스");
        given(courseCategoryService.createCourseCategory(request))
                .willThrow(new ServiceErrorException(ERR_DUPLICATED_CATEGORY_CODE));

        assertThatThrownBy(() -> courseCategoryController.createCourseCategory(request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("이미 존재하는 카테고리 코드입니다");
    }
    // endregion
}
