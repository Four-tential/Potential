package four_tential.potential.infra.oauth2;

import four_tential.potential.application.auth.OAuthLoginTicketData;
import four_tential.potential.application.auth.OAuthRedirectTicketRepository;
import four_tential.potential.domain.member.member.MemberRole;
import four_tential.potential.infra.jwt.JwtRepository;
import four_tential.potential.infra.jwt.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private JwtRepository jwtRepository;

    @Mock
    private OAuthRedirectTicketRepository ticketRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private OAuth2LoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "refreshTokenExpire", 604_800_000L);
        ReflectionTestUtils.setField(handler, "successRedirectUri", "http://localhost:8080/oauth-success.html");
        ReflectionTestUtils.setField(handler, "cookieSecure", false);
    }

    @Test
    @DisplayName("성공 시 - 티켓 발급 + ticket 쿼리만 포함하는 redirect URL 생성, accessToken 은 URL 에 노출되지 않음")
    void onSuccess_redirectsWithTicketOnly() throws Exception {
        UUID memberId = UUID.randomUUID();
        CustomOAuth2User principal = new CustomOAuth2User(
                memberId, "user@example.com", MemberRole.ROLE_STUDENT, true, false, Map.of()
        );
        given(authentication.getPrincipal()).willReturn(principal);
        given(jwtUtil.createAccessToken("user@example.com", memberId, "ROLE_STUDENT"))
                .willReturn("access-token");
        given(jwtUtil.createRefreshToken("user@example.com")).willReturn("refresh-token");
        given(ticketRepository.issueLogin(any(OAuthLoginTicketData.class))).willReturn("ticket-uuid");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:8080/oauth-success.html?ticket=ticket-uuid");
        assertThat(response.getRedirectedUrl()).doesNotContain("access-token");

        ArgumentCaptor<OAuthLoginTicketData> captor = ArgumentCaptor.forClass(OAuthLoginTicketData.class);
        verify(ticketRepository).issueLogin(captor.capture());
        assertThat(captor.getValue().accessToken()).isEqualTo("access-token");
        assertThat(captor.getValue().hasOnboarding()).isTrue();
        assertThat(captor.getValue().requiresPhoneSetup()).isFalse();

        verify(jwtRepository).saveRefreshToken(eq("user@example.com"), eq("refresh-token"), anyLong());
    }

    @Test
    @DisplayName("성공 시 - refreshToken 쿠키가 Set-Cookie 로 응답에 추가됨")
    void onSuccess_addsRefreshCookie() throws Exception {
        UUID memberId = UUID.randomUUID();
        CustomOAuth2User principal = new CustomOAuth2User(
                memberId, "user@example.com", MemberRole.ROLE_STUDENT, false, true, Map.of()
        );
        given(authentication.getPrincipal()).willReturn(principal);
        given(jwtUtil.createAccessToken(anyString(), any(UUID.class), anyString())).willReturn("at");
        given(jwtUtil.createRefreshToken(anyString())).willReturn("rt-value");
        given(ticketRepository.issueLogin(any(OAuthLoginTicketData.class))).willReturn("t1");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("refreshToken=rt-value");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("Path=/v1/auth");
        assertThat(setCookie).contains("SameSite=Strict");
    }
}
