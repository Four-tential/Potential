package four_tential.potential.infra.oauth2;

import four_tential.potential.application.auth.OAuthLinkTicketData;
import four_tential.potential.application.auth.OAuthRedirectTicketRepository;
import four_tential.potential.domain.member.social.SocialProvider;
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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginFailureHandlerTest {

    @Mock
    private OAuthRedirectTicketRepository ticketRepository;

    @InjectMocks
    private OAuth2LoginFailureHandler handler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "failureRedirectUri", "http://localhost:8080/oauth-failure.html");
        ReflectionTestUtils.setField(handler, "linkConfirmRedirectUri", "http://localhost:8080/oauth-link-confirm.html");
    }

    @Test
    @DisplayName("이메일 충돌 - linkTicket 발급 후 link-confirm 페이지로 redirect, challengeToken 은 URL 에 노출되지 않음")
    void emailConflict_redirectsWithLinkTicket() throws Exception {
        OAuth2Error err = new OAuth2Error(
                "ERR_SOCIAL_EMAIL_CONFLICT",
                "challengeToken=tok-1&email=user@example.com&provider=KAKAO",
                null
        );
        OAuth2AuthenticationException oauth2Ex = new OAuth2AuthenticationException(err);
        InternalAuthenticationServiceException wrapped = new InternalAuthenticationServiceException(err.getDescription(), oauth2Ex);

        given(ticketRepository.issueLink(new OAuthLinkTicketData("tok-1", "user@example.com", SocialProvider.KAKAO)))
                .willReturn("link-ticket-uuid");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, wrapped);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:8080/oauth-link-confirm.html?linkTicket=link-ticket-uuid");
        assertThat(response.getRedirectedUrl()).doesNotContain("challengeToken");
        assertThat(response.getRedirectedUrl()).doesNotContain("user@example.com");

        ArgumentCaptor<OAuthLinkTicketData> captor = ArgumentCaptor.forClass(OAuthLinkTicketData.class);
        verify(ticketRepository).issueLink(captor.capture());
        assertThat(captor.getValue().challengeToken()).isEqualTo("tok-1");
        assertThat(captor.getValue().email()).isEqualTo("user@example.com");
        assertThat(captor.getValue().provider()).isEqualTo(SocialProvider.KAKAO);
    }

    @Test
    @DisplayName("이메일 충돌 - description 누락 시 missing_challenge 쿼리로 redirect, 티켓 발급 안 함")
    void emailConflict_missingDescription() throws Exception {
        OAuth2Error err = new OAuth2Error("ERR_SOCIAL_EMAIL_CONFLICT", null, null);
        OAuth2AuthenticationException oauth2Ex = new OAuth2AuthenticationException(err);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, oauth2Ex);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:8080/oauth-link-confirm.html?error=missing_challenge");
        verify(ticketRepository, never()).issueLink(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("일반 OAuth2 실패 - 실패 redirect URI 로 error 코드 전달")
    void generalFailure_redirectsToFailureUri() throws Exception {
        OAuth2Error err = new OAuth2Error("invalid_token_response", "토큰 응답 오류", null);
        OAuth2AuthenticationException oauth2Ex = new OAuth2AuthenticationException(err);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, oauth2Ex);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:8080/oauth-failure.html?error=invalid_token_response");
        verify(ticketRepository, never()).issueLink(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("OAuth2 가 아닌 일반 인증 예외 - 기본 error 코드 oauth2_login_failed")
    void nonOAuth2Exception() throws Exception {
        BadCredentialsException ex = new BadCredentialsException("bad");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, ex);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:8080/oauth-failure.html?error=oauth2_login_failed");
    }
}
