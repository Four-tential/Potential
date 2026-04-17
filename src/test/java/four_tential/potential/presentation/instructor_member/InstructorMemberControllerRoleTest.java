package four_tential.potential.presentation.instructor_member;

import four_tential.potential.application.instructor_member.InstructorMemberService;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.GlobalExceptionHandler;
import four_tential.potential.domain.member.instructor_member.InstructorMemberStatus;
import four_tential.potential.infra.jwt.JwtFilter;
import four_tential.potential.infra.security.SecurityConfig;
import four_tential.potential.presentation.instructor_member.model.request.InstructorAction;
import four_tential.potential.presentation.instructor_member.model.request.InstructorActionRequest;
import four_tential.potential.presentation.instructor_member.model.response.InstructorActionResponse;
import four_tential.potential.presentation.instructor_member.model.response.InstructorApplicationDetail;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InstructorMemberController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class InstructorMemberControllerRoleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InstructorMemberService instructorMemberService;

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
    @DisplayName("ADMIN은 강사 신청 목록 조회 가능 - 200 OK")
    void getInstructorApplications_admin_success() throws Exception {
        given(instructorMemberService.getInstructorApplications(any(), any()))
                .willReturn(new PageResponse<>(List.of(), 0, 0, 0, 10, true));

        mockMvc.perform(get("/v1/admin/instructor-applications")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("STUDENT는 강사 신청 목록 조회 불가 - 403 Forbidden")
    void getInstructorApplications_student_forbidden() throws Exception {
        mockMvc.perform(get("/v1/admin/instructor-applications"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    @DisplayName("INSTRUCTOR는 강사 신청 목록 조회 불가 - 403 Forbidden")
    void getInstructorApplications_instructor_forbidden() throws Exception {
        mockMvc.perform(get("/v1/admin/instructor-applications"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN은 강사 신청 상세 조회 가능 - 200 OK")
    void getInstructorApplicationDetail_admin_success() throws Exception {
        UUID memberId = UUID.randomUUID();
        InstructorApplicationDetail detail = new InstructorApplicationDetail(
                memberId, "홍길동", "hong@test.com", "010-1234-5678",
                "FITNESS", "피트니스", "10년 경력", "https://example.com/cert.jpg",
                InstructorMemberStatus.PENDING, null, null, null
        );
        given(instructorMemberService.getInstructorApplicationDetail(memberId)).willReturn(detail);

        mockMvc.perform(get("/v1/admin/instructor-applications/{memberId}", memberId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("STUDENT는 강사 신청 상세 조회 불가 - 403 Forbidden")
    void getInstructorApplicationDetail_student_forbidden() throws Exception {
        mockMvc.perform(get("/v1/admin/instructor-applications/{memberId}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    @DisplayName("INSTRUCTOR는 강사 신청 상세 조회 불가 - 403 Forbidden")
    void getInstructorApplicationDetail_instructor_forbidden() throws Exception {
        mockMvc.perform(get("/v1/admin/instructor-applications/{memberId}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN은 강사 신청 승인/반려 가능 - 200 OK")
    void processInstructorApplication_admin_success() throws Exception {
        UUID memberId = UUID.randomUUID();
        InstructorActionRequest request = new InstructorActionRequest(InstructorAction.APPROVE, null);
        InstructorActionResponse serviceResponse = new InstructorActionResponse(
                memberId, InstructorMemberStatus.APPROVED, LocalDateTime.now()
        );
        given(instructorMemberService.processInstructorApplication(any(), any())).willReturn(serviceResponse);

        mockMvc.perform(patch("/v1/admin/instructor-applications/{memberId}", memberId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("STUDENT는 강사 신청 처리 불가 - 403 Forbidden")
    void processInstructorApplication_student_forbidden() throws Exception {
        InstructorActionRequest request = new InstructorActionRequest(InstructorAction.APPROVE, null);

        mockMvc.perform(patch("/v1/admin/instructor-applications/{memberId}", UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
