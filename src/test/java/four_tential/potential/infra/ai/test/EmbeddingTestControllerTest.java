package four_tential.potential.infra.ai.test;

import four_tential.potential.infra.ai.vector.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmbeddingTestController.class)
@ActiveProfiles("local")
@WithMockUser
class EmbeddingTestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VectorStoreService vectorStoreService;

    @MockitoBean
    private four_tential.potential.infra.jwt.JwtUtil jwtUtil;

    @MockitoBean
    private four_tential.potential.infra.jwt.JwtRepository jwtRepository;

    private static final String BEARER_TOKEN = "Bearer test-token";

    @BeforeEach
    void setUp() {
        // JwtFilter 통과용 Mock 설정
        when(jwtUtil.validateToken("test-token")).thenReturn(true);
        when(jwtRepository.isBlacklist("test-token")).thenReturn(false);
        when(jwtUtil.extractSubject("test-token")).thenReturn("test@test.com");
        when(jwtUtil.extractRoleByToken("test-token")).thenReturn("ROLE_USER");
        when(jwtUtil.extractMemberIdByToken("test-token")).thenReturn(UUID.randomUUID().toString());
    }

    @Test
    void embed_단건저장_성공() throws Exception {
        mockMvc.perform(post("/ai/test/embed")
                        .with(csrf())
                        .header("Authorization", BEARER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"domain":"review","entityId":1,"content":"좋은 강의입니다"}
                                """))
                .andExpect(status().isOk());

        verify(vectorStoreService).add("review", 1L, "좋은 강의입니다");
    }

    @Test
    void embedBatch_배치저장_성공() throws Exception {
        mockMvc.perform(post("/ai/test/embed/batch")
                        .with(csrf())
                        .header("Authorization", BEARER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"domain":"review","entityId":2,"contents":["리뷰1","리뷰2"]}
                                """))
                .andExpect(status().isOk());

        verify(vectorStoreService).addBatch("review", 2L, List.of("리뷰1", "리뷰2"));
    }

    @Test
    void search_유사도검색_결과반환() throws Exception {
        when(vectorStoreService.search("review", 1L, "좋아요"))
                .thenReturn(List.of("좋은 강의입니다", "추천합니다"));

        mockMvc.perform(get("/ai/test/search")
                        .header("Authorization", BEARER_TOKEN)
                        .param("domain", "review")
                        .param("entityId", "1")
                        .param("query", "좋아요"))
                .andExpect(status().isOk());
    }

    @Test
    void search_결과없음_빈리스트반환() throws Exception {
        when(vectorStoreService.search(any(), anyLong(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/ai/test/search")
                        .header("Authorization", BEARER_TOKEN)
                        .param("domain", "review")
                        .param("entityId", "1")
                        .param("query", "없는내용"))
                .andExpect(status().isOk());
    }

    @Test
    void delete_삭제_성공() throws Exception {
        mockMvc.perform(delete("/ai/test")
                        .with(csrf())
                        .header("Authorization", BEARER_TOKEN)
                        .param("domain", "review")
                        .param("entityId", "1"))
                .andExpect(status().isOk());

        verify(vectorStoreService).delete("review", 1L);
    }
}