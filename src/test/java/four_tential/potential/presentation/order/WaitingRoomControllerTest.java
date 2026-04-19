package four_tential.potential.presentation.order;

import four_tential.potential.application.order.WaitingListService;
import four_tential.potential.common.exception.GlobalExceptionHandler;
import four_tential.potential.infra.jwt.JwtFilter;
import four_tential.potential.infra.security.SecurityConfig;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.infra.sse.SseWaitingEventPublisher;
import four_tential.potential.infra.sse.SseWaitingRoomRepository;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WaitingRoomController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class WaitingRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WaitingListService waitingListService;

    @MockitoBean
    private SseWaitingRoomRepository sseWaitingRoomRepository;

    @MockitoBean
    private SseWaitingEventPublisher sseWaitingEventPublisher;

    @MockitoBean
    private JwtFilter jwtFilter;

    private final UUID courseId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final MemberPrincipal principal = new MemberPrincipal(memberId, "test@test.com", "ROLE_STUDENT");
    private final UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
    );

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
    @DisplayName("대기열 스트리밍 연결 시 SseEmitter를 반환하고 초기 순번을 전송한다")
    @WithMockUser(roles = "STUDENT")
    void streamWaitingStatus_success() throws Exception {
        // given
        given(waitingListService.getWaitingRank(courseId, memberId)).willReturn(1L);
        given(waitingListService.getWaitingListSize(courseId)).willReturn(10);

        // when & then
        mockMvc.perform(get("/v1/orders/waiting-room/stream")
                        .param("courseId", courseId.toString())
                        .with(authentication(authentication))
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk());

        verify(sseWaitingRoomRepository).save(eq(courseId), eq(memberId), any());
        verify(sseWaitingEventPublisher).publish(eq(courseId), eq(memberId), any());
    }

    @Test
    @DisplayName("대기열 이탈 요청 시 성공적으로 처리한다")
    @WithMockUser(roles = "STUDENT")
    void leaveWaitingRoom_success() throws Exception {
        // when & then
        mockMvc.perform(delete("/v1/orders/waiting-room")
                        .param("courseId", courseId.toString())
                        .with(authentication(authentication))
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(waitingListService).removeFromWaitingList(courseId, memberId);
        verify(sseWaitingRoomRepository).delete(courseId, memberId);
    }
}
