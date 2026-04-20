package four_tential.potential.presentation.course;

import four_tential.potential.application.course.CourseCategoryService;
import four_tential.potential.common.exception.GlobalExceptionHandler;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.infra.jwt.JwtFilter;
import four_tential.potential.infra.security.SecurityConfig;
import four_tential.potential.presentation.course.model.request.CreateCourseCategoryRequest;
import four_tential.potential.presentation.course.model.request.UpdateCourseCategoryRequest;
import four_tential.potential.presentation.course.model.response.CreateCourseCategoryResponse;
import four_tential.potential.presentation.course.model.response.UpdateCourseCategoryResponse;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseCategoryController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class CourseCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CourseCategoryService courseCategoryService;

    @MockitoBean
    private JwtFilter jwtFilter;

    @BeforeEach
    void setUp() throws Exception {
        willAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).given(jwtFilter).doFilter(any(), any(), any());
    }

    // region createCourseCategory
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("카테고리 생성 - ADMIN이면 201 CREATED 및 생성 정보 반환")
    void createCourseCategory_success() throws Exception {
        CreateCourseCategoryRequest request = new CreateCourseCategoryRequest("DANCE", "댄스");
        CreateCourseCategoryResponse serviceResponse = new CreateCourseCategoryResponse(
                "DANCE", "댄스", LocalDateTime.now()
        );
        given(courseCategoryService.createCourseCategory(any())).willReturn(serviceResponse);

        mockMvc.perform(post("/v1/admin/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("DANCE"))
                .andExpect(jsonPath("$.data.name").value("댄스"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("카테고리 생성 - ADMIN이 아니면 403 Forbidden")
    void createCourseCategory_notAdmin() throws Exception {
        CreateCourseCategoryRequest request = new CreateCourseCategoryRequest("DANCE", "댄스");

        mockMvc.perform(post("/v1/admin/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("카테고리 생성 - 중복 코드이면 409 Conflict")
    void createCourseCategory_duplicatedCode() throws Exception {
        CreateCourseCategoryRequest request = new CreateCourseCategoryRequest("DANCE", "댄스");
        given(courseCategoryService.createCourseCategory(any()))
                .willThrow(new ServiceErrorException(ERR_DUPLICATED_CATEGORY_CODE));

        mockMvc.perform(post("/v1/admin/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }
    // endregion

    // region updateCategoryName
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("카테고리 이름 수정 - ADMIN이면 200 OK 및 수정 정보 반환")
    void updateCategoryName_success() throws Exception {
        UpdateCourseCategoryRequest request = new UpdateCourseCategoryRequest("댄스/무용");
        UpdateCourseCategoryResponse serviceResponse =
                new UpdateCourseCategoryResponse("DANCE", "댄스/무용", LocalDateTime.of(2026, 1, 20, 9, 0));

        given(courseCategoryService.updateCategoryName(eq("DANCE"), any())).willReturn(serviceResponse);

        mockMvc.perform(patch("/v1/admin/categories/DANCE")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("카테고리 수정 성공"))
                .andExpect(jsonPath("$.data.code").value("DANCE"))
                .andExpect(jsonPath("$.data.name").value("댄스/무용"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("카테고리 이름 수정 - ADMIN이 아니면 403 Forbidden")
    void updateCategoryName_notAdmin() throws Exception {
        UpdateCourseCategoryRequest request = new UpdateCourseCategoryRequest("댄스/무용");

        mockMvc.perform(patch("/v1/admin/categories/DANCE")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("카테고리 이름 수정 - 존재하지 않는 코드이면 404 Not Found")
    void updateCategoryName_notFound() throws Exception {
        UpdateCourseCategoryRequest request = new UpdateCourseCategoryRequest("댄스/무용");

        given(courseCategoryService.updateCategoryName(eq("UNKNOWN"), any()))
                .willThrow(new ServiceErrorException(ERR_CATEGORY_NOT_FOUND));

        mockMvc.perform(patch("/v1/admin/categories/UNKNOWN")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("카테고리 이름 수정 - name이 빈 값이면 400 Bad Request")
    void updateCategoryName_blankName() throws Exception {
        UpdateCourseCategoryRequest request = new UpdateCourseCategoryRequest("");

        mockMvc.perform(patch("/v1/admin/categories/DANCE")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    // endregion

    // region deleteCategory
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("카테고리 삭제 - ADMIN이면 200 OK")
    void deleteCategory_success() throws Exception {
        willDoNothing().given(courseCategoryService).deleteCategory("DANCE");

        mockMvc.perform(delete("/v1/admin/categories/DANCE")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("카테고리 삭제 성공"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("카테고리 삭제 - ADMIN이 아니면 403 Forbidden")
    void deleteCategory_notAdmin() throws Exception {
        mockMvc.perform(delete("/v1/admin/categories/DANCE")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("카테고리 삭제 - 존재하지 않는 코드이면 404 Not Found")
    void deleteCategory_notFound() throws Exception {
        willThrow(new ServiceErrorException(ERR_CATEGORY_NOT_FOUND))
                .given(courseCategoryService).deleteCategory("UNKNOWN");

        mockMvc.perform(delete("/v1/admin/categories/UNKNOWN")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("카테고리 삭제 - 코스 또는 강사가 존재하면 409 Conflict")
    void deleteCategory_conflict() throws Exception {
        willThrow(new ServiceErrorException(ERR_CATEGORY_HAS_COURSES_OR_INSTRUCTORS))
                .given(courseCategoryService).deleteCategory("DANCE");

        mockMvc.perform(delete("/v1/admin/categories/DANCE")
                        .with(csrf()))
                .andExpect(status().isConflict());
    }
    // endregion
}
