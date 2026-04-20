package four_tential.potential.presentation.course;

import four_tential.potential.application.course.CourseService;
import four_tential.potential.common.exception.GlobalExceptionHandler;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.infra.jwt.JwtFilter;
import four_tential.potential.infra.security.SecurityConfig;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.course.model.request.CreateCourseRequestRequest;
import four_tential.potential.presentation.course.model.response.CreateCourseRequestResponse;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.*;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_NOT_FOUND_INSTRUCTOR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseRequestController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class CourseRequestControllerTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final MemberPrincipal INSTRUCTOR_PRINCIPAL =
            new MemberPrincipal(MEMBER_ID, "instructor@example.com", "ROLE_INSTRUCTOR");

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

    private RequestPostProcessor instructorAuth() {
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        INSTRUCTOR_PRINCIPAL, null,
                        List.of(new SimpleGrantedAuthority("ROLE_INSTRUCTOR"))
                )
        );
    }

    @Test
    @DisplayName("코스 개설 신청 - INSTRUCTOR이면 201 CREATED 및 코스 정보 반환")
    void createCourseRequest_success() throws Exception {
        CreateCourseRequestResponse serviceResponse = new CreateCourseRequestResponse(
                UUID.randomUUID(), "소도구 필라테스 입문반", "FITNESS", CourseStatus.PREPARATION
        );
        given(courseService.createCourseRequest(any(), any())).willReturn(serviceResponse);

        mockMvc.perform(post("/v1/course-requests")
                        .with(instructorAuth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("코스가 개설 신청 성공"))
                .andExpect(jsonPath("$.data.title").value("소도구 필라테스 입문반"))
                .andExpect(jsonPath("$.data.categoryCode").value("FITNESS"))
                .andExpect(jsonPath("$.data.status").value("PREPARATION"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("코스 개설 신청 - INSTRUCTOR가 아니면 403 Forbidden")
    void createCourseRequest_notInstructor() throws Exception {
        mockMvc.perform(post("/v1/course-requests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("코스 개설 신청 - 미승인 강사이면 404 Not Found")
    void createCourseRequest_notApprovedInstructor() throws Exception {
        given(courseService.createCourseRequest(any(), any()))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));

        mockMvc.perform(post("/v1/course-requests")
                        .with(instructorAuth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("코스 개설 신청 - 잘못된 강의 시간이면 400 Bad Request")
    void createCourseRequest_invalidSchedule() throws Exception {
        given(courseService.createCourseRequest(any(), any()))
                .willThrow(new ServiceErrorException(ERR_INVALID_SCHEDULE));

        mockMvc.perform(post("/v1/course-requests")
                        .with(instructorAuth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("코스 개설 신청 - 잘못된 주문 마감 시간이면 400 Bad Request")
    void createCourseRequest_invalidOrderCloseTime() throws Exception {
        given(courseService.createCourseRequest(any(), any()))
                .willThrow(new ServiceErrorException(ERR_INVALID_ORDER_CLOSE_TIME));

        mockMvc.perform(post("/v1/course-requests")
                        .with(instructorAuth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("코스 개설 신청 - 필수 필드가 빈 값이면 400 Bad Request")
    void createCourseRequest_blankTitle() throws Exception {
        CreateCourseRequestRequest invalid = new CreateCourseRequestRequest(
                "", "설명", "주소", "상세주소",
                BigInteger.valueOf(50000), 10,
                LocalDateTime.now().plusDays(10),
                LocalDateTime.now().plusDays(20),
                LocalDateTime.now().plusDays(30),
                LocalDateTime.now().plusDays(30).plusHours(2),
                CourseLevel.BEGINNER, null
        );

        mockMvc.perform(post("/v1/course-requests")
                        .with(instructorAuth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("코스 개설 신청 취소 - INSTRUCTOR이면 200 OK")
    void deleteCourseRequest_success() throws Exception {
        UUID courseId = UUID.randomUUID();
        willDoNothing().given(courseService).deleteCourseRequest(any(), eq(courseId));

        mockMvc.perform(delete("/v1/course-requests/{courseId}", courseId)
                        .with(instructorAuth())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("코스 개설 신청 삭제"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("코스 개설 신청 취소 - INSTRUCTOR가 아니면 403 Forbidden")
    void deleteCourseRequest_notInstructor() throws Exception {
        mockMvc.perform(delete("/v1/course-requests/{courseId}", UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("코스 개설 신청 취소 - 존재하지 않는 코스이면 404 Not Found")
    void deleteCourseRequest_courseNotFound() throws Exception {
        UUID courseId = UUID.randomUUID();
        willThrow(new ServiceErrorException(ERR_NOT_FOUND_COURSE))
                .given(courseService).deleteCourseRequest(any(), eq(courseId));

        mockMvc.perform(delete("/v1/course-requests/{courseId}", courseId)
                        .with(instructorAuth())
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("코스 개설 신청 취소 - 본인 코스가 아니면 403 Forbidden")
    void deleteCourseRequest_notOwnCourse() throws Exception {
        UUID courseId = UUID.randomUUID();
        willThrow(new ServiceErrorException(ERR_FORBIDDEN_COURSE))
                .given(courseService).deleteCourseRequest(any(), eq(courseId));

        mockMvc.perform(delete("/v1/course-requests/{courseId}", courseId)
                        .with(instructorAuth())
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("코스 개설 신청 취소 - PREPARATION이 아닌 코스이면 400 Bad Request")
    void deleteCourseRequest_notPreparation() throws Exception {
        UUID courseId = UUID.randomUUID();
        willThrow(new ServiceErrorException(ERR_CANNOT_DELETE_COURSE_REQUEST))
                .given(courseService).deleteCourseRequest(any(), eq(courseId));

        mockMvc.perform(delete("/v1/course-requests/{courseId}", courseId)
                        .with(instructorAuth())
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    private CreateCourseRequestRequest defaultRequest() {
        return new CreateCourseRequestRequest(
                "소도구 필라테스 입문반",
                "소도구를 활용한 전신 필라테스 수업입니다.",
                "서울시 강남구 테헤란로 123",
                "3층 필라테스룸",
                BigInteger.valueOf(70000),
                10,
                LocalDateTime.now().plusDays(10),
                LocalDateTime.now().plusDays(20),
                LocalDateTime.now().plusDays(30),
                LocalDateTime.now().plusDays(30).plusHours(2),
                CourseLevel.BEGINNER,
                List.of("https://cdn.example.com/img1.jpg")
        );
    }
}
