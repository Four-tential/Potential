package four_tential.potential.presentation.instructor_member;

import four_tential.potential.application.instructor_member.InstructorMemberService;
import four_tential.potential.common.exception.GlobalExceptionHandler;
import four_tential.potential.infra.jwt.JwtFilter;
import four_tential.potential.infra.security.SecurityConfig;
import four_tential.potential.presentation.instructor_member.model.request.ApplyInstructorRequest;
import four_tential.potential.presentation.instructor_member.model.request.InstructorAction;
import four_tential.potential.presentation.instructor_member.model.request.InstructorActionRequest;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InstructorMemberController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class InstructorMemberControllerValidationTest {

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
        willAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).given(jwtFilter).doFilter(any(), any(), any());
    }

    // region applyInstructor 유효성 검증
    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("강사 신청 - categoryCode가 빈 문자열이면 400 Bad Request")
    void applyInstructor_blankCategoryCode_badRequest() throws Exception {
        ApplyInstructorRequest request = new ApplyInstructorRequest(
                "", "10년 경력의 피트니스 강사입니다", "https://example.com/cert.jpg"
        );

        mockMvc.perform(post("/v1/members/me/instructor-applications")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("강사 신청 - content가 빈 문자열이면 400 Bad Request")
    void applyInstructor_blankContent_badRequest() throws Exception {
        ApplyInstructorRequest request = new ApplyInstructorRequest(
                "FITNESS", "", "https://example.com/cert.jpg"
        );

        mockMvc.perform(post("/v1/members/me/instructor-applications")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("강사 신청 - imageUrl이 http/https 형식이 아니면 400 Bad Request")
    void applyInstructor_invalidImageUrl_badRequest() throws Exception {
        ApplyInstructorRequest request = new ApplyInstructorRequest(
                "FITNESS", "10년 경력의 피트니스 강사입니다", "ftp://invalid.com/image.jpg"
        );

        mockMvc.perform(post("/v1/members/me/instructor-applications")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("강사 신청 - categoryCode가 20자 초과이면 400 Bad Request")
    void applyInstructor_categoryCodeTooLong_badRequest() throws Exception {
        ApplyInstructorRequest request = new ApplyInstructorRequest(
                "A".repeat(21), "10년 경력의 피트니스 강사입니다", "https://example.com/cert.jpg"
        );

        mockMvc.perform(post("/v1/members/me/instructor-applications")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("강사 신청 - imageUrl이 300자 초과이면 400 Bad Request")
    void applyInstructor_imageUrlTooLong_badRequest() throws Exception {
        String longUrl = "https://example.com/" + "a".repeat(290);
        ApplyInstructorRequest request = new ApplyInstructorRequest(
                "FITNESS", "10년 경력의 피트니스 강사입니다", longUrl
        );

        mockMvc.perform(post("/v1/members/me/instructor-applications")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    // endregion

    // region processInstructorApplication 유효성 검증
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("강사 신청 처리 - action이 null이면 400 Bad Request")
    void processInstructorApplication_nullAction_badRequest() throws Exception {
        InstructorActionRequest request = new InstructorActionRequest(null, "사유 없음");

        mockMvc.perform(patch("/v1/admin/instructor-applications/{memberId}", UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("강사 신청 처리 - 요청 바디가 없으면 400 Bad Request")
    void processInstructorApplication_emptyBody_badRequest() throws Exception {
        mockMvc.perform(patch("/v1/admin/instructor-applications/{memberId}", UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("강사 신청 처리 - APPROVE action은 rejectReason 없어도 200 가능")
    void processInstructorApplication_approveWithoutReason_ok() throws Exception {
        InstructorActionRequest request = new InstructorActionRequest(InstructorAction.APPROVE, null);

        mockMvc.perform(patch("/v1/admin/instructor-applications/{memberId}", UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
    // endregion
}
