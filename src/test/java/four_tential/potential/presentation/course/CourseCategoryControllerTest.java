package four_tential.potential.presentation.course;

import four_tential.potential.application.course.CourseCategoryService;
import four_tential.potential.common.exception.GlobalExceptionHandler;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.infra.jwt.JwtFilter;
import four_tential.potential.infra.security.SecurityConfig;
import four_tential.potential.presentation.course.model.request.CreateCourseCategoryRequest;
import four_tential.potential.presentation.course.model.response.CreateCourseCategoryResponse;
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

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_DUPLICATED_CATEGORY_CODE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        // JwtFilter mock이 filter chain을 통과시키도록 설정
        willAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).given(jwtFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("카테고리 생성 - ADMIN 이면 201 CREATED 및 생성 정보 반환")
    void createCourseCategory_success() throws Exception {
        CreateCourseCategoryRequest request = new CreateCourseCategoryRequest("DANCE", "댄스");
        CreateCourseCategoryResponse serviceResponse = new CreateCourseCategoryResponse(
                "DANCE", "댄스", LocalDateTime.now()
        );
        given(courseCategoryService.createCourseCategory(any())).willReturn(serviceResponse);

        mockMvc.perform(post("/v1/course-categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("DANCE"))
                .andExpect(jsonPath("$.data.name").value("댄스"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("카테고리 생성 - ADMIN 이 아니면 403 Forbidden 반환")
    void createCourseCategory_notAdmin() throws Exception {
        CreateCourseCategoryRequest request = new CreateCourseCategoryRequest("DANCE", "댄스");
        mockMvc.perform(post("/v1/course-categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("카테고리 생성 - 중복 코드이면 ServiceErrorException 전파")
    void createCourseCategory_duplicatedCode() throws Exception {
        CreateCourseCategoryRequest request = new CreateCourseCategoryRequest("DANCE", "댄스");
        given(courseCategoryService.createCourseCategory(any()))
                .willThrow(new ServiceErrorException(ERR_DUPLICATED_CATEGORY_CODE));

        mockMvc.perform(post("/v1/course-categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }
}
