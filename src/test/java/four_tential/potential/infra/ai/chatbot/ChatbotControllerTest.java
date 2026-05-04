package four_tential.potential.infra.ai.chatbot;

import four_tential.potential.infra.jwt.JwtRepository;
import four_tential.potential.infra.jwt.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatbotController.class)
@ActiveProfiles("local")
@WithMockUser
class ChatbotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatbotService chatbotService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private JwtRepository jwtRepository;

    private static final String BEARER_TOKEN = "Bearer test-token";

    @BeforeEach
    void setUp() {
        when(jwtUtil.validateToken("test-token")).thenReturn(true);
        when(jwtRepository.isBlacklist("test-token")).thenReturn(false);
        when(jwtUtil.extractSubject("test-token")).thenReturn("test@test.com");
        when(jwtUtil.extractRoleByToken("test-token")).thenReturn("ROLE_USER");
        when(jwtUtil.extractMemberIdByToken("test-token")).thenReturn(UUID.randomUUID().toString());
    }

    @Test
    @DisplayName("POST /v1/chatbot — 정상 질문이면 200 과 답변 반환")
    void ask_success() throws Exception {
        when(chatbotService.ask("환불 기간이 어떻게 되나요"))
                .thenReturn("코스 시작 7일 전까지 환불 가능합니다.");

        mockMvc.perform(post("/v1/chatbot")
                        .with(csrf())
                        .header("Authorization", BEARER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"환불 기간이 어떻게 되나요"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("코스 시작 7일 전까지 환불 가능합니다."));

        verify(chatbotService).ask("환불 기간이 어떻게 되나요");
    }

    @Test
    @DisplayName("POST /v1/chatbot — question 이 공백이면 400 반환")
    void ask_blank_question_returns_400() throws Exception {
        mockMvc.perform(post("/v1/chatbot")
                        .with(csrf())
                        .header("Authorization", BEARER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"   "}
                                """))
                .andExpect(status().isBadRequest());
    }
}
