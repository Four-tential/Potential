package four_tential.potential.presentation.course;

import four_tential.potential.application.course.CourseService;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.GlobalExceptionHandler;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.infra.jwt.JwtFilter;
import four_tential.potential.infra.security.SecurityConfig;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.course.model.request.UpdateCourseRequest;
import four_tential.potential.presentation.course.model.response.CourseDetailInstructorInfo;
import four_tential.potential.presentation.course.model.response.CourseDetailResponse;
import four_tential.potential.presentation.course.model.response.CourseListInstructorInfo;
import four_tential.potential.presentation.course.model.response.CourseListItem;
import four_tential.potential.presentation.course.model.response.CourseWishlistResponse;
import four_tential.potential.presentation.course.model.response.UpdateCourseResponse;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_ALREADY_WISHLISTED;
import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_FORBIDDEN_COURSE_CLOSE;
import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_INVALID_STATUS_TRANSITION_TO_CLOSE;
import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_NOT_FOUND_COURSE;
import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_WISHLIST_NOT_FOUND;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class CourseControllerTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final MemberPrincipal MEMBER_PRINCIPAL =
            new MemberPrincipal(MEMBER_ID, "member@example.com", "ROLE_MEMBER");
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

    private RequestPostProcessor memberAuth() {
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        MEMBER_PRINCIPAL, null,
                        List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
                )
        );
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
    @DisplayName("코스 목록 조회 - 비인증 유저도 200 OK")
    void getCourses_anonymous_success() throws Exception {
        given(courseService.getCourses(any(), any(), any()))
                .willReturn(PageResponse.register(
                        new org.springframework.data.domain.PageImpl<>(List.of())
                ));

        mockMvc.perform(get("/v1/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("코스 목록 조회 성공"));
    }

    @Test
    @DisplayName("코스 목록 조회 - 조건 파라미터와 함께 200 OK")
    void getCourses_withParams_success() throws Exception {
        CourseListItem item = new CourseListItem(
                UUID.randomUUID(), "테스트 코스", "BACKEND", "백엔드",
                new CourseListInstructorInfo(UUID.randomUUID(), "강사", "https://cdn.example.com/profile.jpg"),
                "https://cdn.example.com/thumb.jpg",
                BigInteger.valueOf(50000), 20, 3,
                CourseStatus.OPEN, CourseLevel.BEGINNER,
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 6, 1, 9, 0),
                false
        );

        given(courseService.getCourses(any(), any(), any()))
                .willReturn(PageResponse.register(
                        new org.springframework.data.domain.PageImpl<>(List.of(item))
                ));

        mockMvc.perform(get("/v1/courses")
                        .param("categoryCode", "BACKEND")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("테스트 코스"))
                .andExpect(jsonPath("$.data.content[0].status").value("OPEN"));
    }

    @Test
    @DisplayName("코스 상세 조회 - 비인증 유저도 200 OK")
    void getCourseDetail_anonymous_success() throws Exception {
        UUID courseId = UUID.randomUUID();
        CourseDetailResponse response = new CourseDetailResponse(
                courseId, "테스트 코스", "설명", "BACKEND", "백엔드",
                new CourseDetailInstructorInfo(UUID.randomUUID(), "강사", "https://cdn.example.com/profile.jpg", 4.5),
                List.of("https://cdn.example.com/img1.jpg"),
                "서울시 강남구", "테헤란로 123",
                BigInteger.valueOf(50000), 20, 5,
                CourseStatus.OPEN, CourseLevel.BEGINNER,
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 20, 9, 0),
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 1, 18, 0),
                4.2, 10L, false
        );

        given(courseService.getCourseDetail(eq(courseId), any())).willReturn(response);

        mockMvc.perform(get("/v1/courses/{courseId}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.courseId").value(courseId.toString()))
                .andExpect(jsonPath("$.data.title").value("테스트 코스"));
    }

    @Test
    @DisplayName("코스 상세 조회 - 존재하지 않는 코스이면 404")
    void getCourseDetail_notFound() throws Exception {
        UUID courseId = UUID.randomUUID();
        given(courseService.getCourseDetail(eq(courseId), any()))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_COURSE));

        mockMvc.perform(get("/v1/courses/{courseId}", courseId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("코스 수정 - INSTRUCTOR이면 200 OK")
    void updateCourse_success() throws Exception {
        UUID courseId = UUID.randomUUID();
        UpdateCourseRequest request = new UpdateCourseRequest(
                "수정된 제목", "수정된 설명",
                CourseLevel.INTERMEDIATE,
                "서울시 서초구", "2층",
                BigInteger.valueOf(65000), 12,
                LocalDateTime.now().plusDays(10),
                LocalDateTime.now().plusDays(20),
                LocalDateTime.now().plusDays(30),
                LocalDateTime.now().plusDays(30).plusHours(2),
                null
        );
        UpdateCourseResponse serviceResponse =
                new UpdateCourseResponse(courseId, "수정된 제목", LocalDateTime.now());

        given(courseService.updateCourse(any(), eq(courseId), any())).willReturn(serviceResponse);

        mockMvc.perform(patch("/v1/courses/{courseId}", courseId)
                        .with(instructorAuth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("코스가 수정되었습니다"))
                .andExpect(jsonPath("$.data.title").value("수정된 제목"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("코스 수정 - INSTRUCTOR가 아니면 403")
    void updateCourse_forbidden() throws Exception {
        UpdateCourseRequest request = new UpdateCourseRequest(
                "수정된 제목", "수정된 설명",
                null, null, null, null, null,
                null, null, null, null, null
        );

        mockMvc.perform(patch("/v1/courses/{courseId}", UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("찜 등록 - 인증 유저이면 201 CREATED")
    void addWishlist_success() throws Exception {
        UUID courseId = UUID.randomUUID();
        given(courseService.addWishlist(any(), eq(courseId)))
                .willReturn(new CourseWishlistResponse(courseId, true));

        mockMvc.perform(post("/v1/courses/{courseId}/wishlist-courses", courseId)
                        .with(memberAuth())
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("찜 목록에 추가 성공"))
                .andExpect(jsonPath("$.data.isWishlisted").value(true));
    }

    @Test
    @DisplayName("찜 등록 - 비인증 유저이면 403")
    void addWishlist_forbidden() throws Exception {
        mockMvc.perform(post("/v1/courses/{courseId}/wishlist-courses", UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("찜 등록 - 존재하지 않는 코스이면 404")
    void addWishlist_courseNotFound() throws Exception {
        UUID courseId = UUID.randomUUID();
        given(courseService.addWishlist(any(), eq(courseId)))
                .willThrow(new ServiceErrorException(ERR_NOT_FOUND_COURSE));

        mockMvc.perform(post("/v1/courses/{courseId}/wishlist-courses", courseId)
                        .with(memberAuth())
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("찜 등록 - 이미 찜한 코스이면 409")
    void addWishlist_alreadyWishlisted() throws Exception {
        UUID courseId = UUID.randomUUID();
        given(courseService.addWishlist(any(), eq(courseId)))
                .willThrow(new ServiceErrorException(ERR_ALREADY_WISHLISTED));

        mockMvc.perform(post("/v1/courses/{courseId}/wishlist-courses", courseId)
                        .with(memberAuth())
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("찜 해제 - 인증 유저이면 200 OK")
    void removeWishlist_success() throws Exception {
        UUID courseId = UUID.randomUUID();
        given(courseService.removeWishlist(any(), eq(courseId)))
                .willReturn(new CourseWishlistResponse(courseId, false));

        mockMvc.perform(delete("/v1/courses/{courseId}/wishlist-courses", courseId)
                        .with(memberAuth())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("찜 목록에서 제거 성공"))
                .andExpect(jsonPath("$.data.isWishlisted").value(false));
    }

    @Test
    @DisplayName("찜 해제 - 비인증 유저이면 403")
    void removeWishlist_unauthorized() throws Exception {
        mockMvc.perform(delete("/v1/courses/{courseId}/wishlist-courses", UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("찜 해제 - 찜 목록에 없으면 404")
    void removeWishlist_notFound() throws Exception {
        UUID courseId = UUID.randomUUID();
        given(courseService.removeWishlist(any(), eq(courseId)))
                .willThrow(new ServiceErrorException(ERR_WISHLIST_NOT_FOUND));

        mockMvc.perform(delete("/v1/courses/{courseId}/wishlist-courses", courseId)
                        .with(memberAuth())
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("코스 종료 - INSTRUCTOR이면 200 OK")
    void closeCourse_success() throws Exception {
        UUID courseId = UUID.randomUUID();
        willDoNothing().given(courseService).closeCourse(any(), eq(courseId));

        mockMvc.perform(patch("/v1/courses/{courseId}/close", courseId)
                        .with(instructorAuth())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("코스 종료 성공"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("코스 종료 - INSTRUCTOR가 아니면 403")
    void closeCourse_forbidden() throws Exception {
        mockMvc.perform(patch("/v1/courses/{courseId}/close", UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("코스 종료 - 존재하지 않는 코스이면 404")
    void closeCourse_courseNotFound() throws Exception {
        UUID courseId = UUID.randomUUID();
        willThrow(new ServiceErrorException(ERR_NOT_FOUND_COURSE))
                .given(courseService).closeCourse(any(), eq(courseId));

        mockMvc.perform(patch("/v1/courses/{courseId}/close", courseId)
                        .with(instructorAuth())
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("코스 종료 - 본인 코스가 아니면 403")
    void closeCourse_notOwnCourse() throws Exception {
        UUID courseId = UUID.randomUUID();
        willThrow(new ServiceErrorException(ERR_FORBIDDEN_COURSE_CLOSE))
                .given(courseService).closeCourse(any(), eq(courseId));

        mockMvc.perform(patch("/v1/courses/{courseId}/close", courseId)
                        .with(instructorAuth())
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("코스 종료 - OPEN이 아닌 코스이면 400")
    void closeCourse_notOpen() throws Exception {
        UUID courseId = UUID.randomUUID();
        willThrow(new ServiceErrorException(ERR_INVALID_STATUS_TRANSITION_TO_CLOSE))
                .given(courseService).closeCourse(any(), eq(courseId));

        mockMvc.perform(patch("/v1/courses/{courseId}/close", courseId)
                        .with(instructorAuth())
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
