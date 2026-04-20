package four_tential.potential.presentation.course;

import four_tential.potential.application.course.CourseService;
import four_tential.potential.common.exception.GlobalExceptionHandler;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.infra.jwt.JwtFilter;
import four_tential.potential.infra.security.SecurityConfig;
import four_tential.potential.domain.course.course_approval_history.CourseApprovalAction;
import four_tential.potential.presentation.course.model.request.CourseRequestActionRequest;
import four_tential.potential.presentation.course.model.response.CourseRequestActionResponse;
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
import java.util.UUID;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_COURSE_NOT_IN_PREPARATION;
import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_NOT_FOUND_COURSE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_REJECT_REASON_REQUIRED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseAdminController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class CourseAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CourseService courseService;

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

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("코스 개설 신청 승인 - ADMIN이면 200 OK 및 OPEN 상태 반환")
    void handleCourseRequest_approve_success() throws Exception {
        UUID courseId = UUID.randomUUID();
        CourseRequestActionRequest request = new CourseRequestActionRequest(CourseApprovalAction.APPROVE, null);
        CourseRequestActionResponse serviceResponse =
                new CourseRequestActionResponse(courseId, CourseStatus.OPEN, LocalDateTime.of(2026, 5, 1, 10, 0));

        given(courseService.handleCourseRequest(eq(courseId), any())).willReturn(serviceResponse);

        mockMvc.perform(patch("/v1/admin/course-requests/{courseId}", courseId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("코스 승인 성공"))
                .andExpect(jsonPath("$.data.courseId").value(courseId.toString()))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.confirmedAt").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("코스 개설 신청 반려 - ADMIN이면 200 OK 및 PREPARATION 상태 유지")
    void handleCourseRequest_reject_success() throws Exception {
        UUID courseId = UUID.randomUUID();
        CourseRequestActionRequest request =
                new CourseRequestActionRequest(CourseApprovalAction.REJECT, "사진 자료 미비");
        CourseRequestActionResponse serviceResponse =
                new CourseRequestActionResponse(courseId, CourseStatus.REJECTED, null);

        given(courseService.handleCourseRequest(eq(courseId), any())).willReturn(serviceResponse);

        mockMvc.perform(patch("/v1/admin/course-requests/{courseId}", courseId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("코스 반려 성공"))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.confirmedAt").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("코스 개설 신청 승인/반려 - ADMIN이 아니면 403 Forbidden")
    void handleCourseRequest_notAdmin() throws Exception {
        CourseRequestActionRequest request = new CourseRequestActionRequest(CourseApprovalAction.APPROVE, null);

        mockMvc.perform(patch("/v1/admin/course-requests/{courseId}", UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("코스 개설 신청 승인/반려 - 존재하지 않는 코스이면 404 Not Found")
    void handleCourseRequest_courseNotFound() throws Exception {
        UUID courseId = UUID.randomUUID();
        CourseRequestActionRequest request = new CourseRequestActionRequest(CourseApprovalAction.APPROVE, null);

        given(courseService.handleCourseRequest(eq(courseId), any()))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_COURSE));

        mockMvc.perform(patch("/v1/admin/course-requests/{courseId}", courseId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("코스 개설 신청 승인/반려 - PREPARATION 상태가 아니면 409 Conflict")
    void handleCourseRequest_notPreparation() throws Exception {
        UUID courseId = UUID.randomUUID();
        CourseRequestActionRequest request = new CourseRequestActionRequest(CourseApprovalAction.APPROVE, null);

        given(courseService.handleCourseRequest(eq(courseId), any()))
                .willThrow(new ServiceErrorException(ERR_COURSE_NOT_IN_PREPARATION));

        mockMvc.perform(patch("/v1/admin/course-requests/{courseId}", courseId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("코스 개설 신청 반려 - rejectReason 없으면 400 Bad Request")
    void handleCourseRequest_rejectWithoutReason() throws Exception {
        UUID courseId = UUID.randomUUID();
        CourseRequestActionRequest request = new CourseRequestActionRequest(CourseApprovalAction.REJECT, null);

        given(courseService.handleCourseRequest(eq(courseId), any()))
                .willThrow(new ServiceErrorException(ERR_REJECT_REASON_REQUIRED));

        mockMvc.perform(patch("/v1/admin/course-requests/{courseId}", courseId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("코스 개설 신청 승인/반려 - action이 null이면 400 Bad Request")
    void handleCourseRequest_missingAction() throws Exception {
        CourseRequestActionRequest request = new CourseRequestActionRequest(null, null);

        mockMvc.perform(patch("/v1/admin/course-requests/{courseId}", UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
