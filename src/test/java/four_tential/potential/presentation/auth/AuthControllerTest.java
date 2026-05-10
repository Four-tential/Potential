package four_tential.potential.presentation.auth;

import four_tential.potential.application.auth.AuthFacade;
import four_tential.potential.application.auth.OAuthLinkTicketData;
import four_tential.potential.application.auth.OAuthLoginTicketData;
import four_tential.potential.application.auth.OAuthRedirectTicketRepository;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.member.social.SocialProvider;
import four_tential.potential.infra.oauth2.OAuth2UserAttributes;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.auth.fixture.LoginRequestFixture;
import four_tential.potential.presentation.auth.fixture.SignUpRequestFixture;
import four_tential.potential.presentation.auth.model.LoginResult;
import four_tential.potential.presentation.auth.model.RefreshResult;
import four_tential.potential.presentation.auth.model.SocialLinkConfirmResult;
import four_tential.potential.presentation.auth.model.request.LoginRequest;
import four_tential.potential.presentation.auth.model.request.OAuthTicketExchangeRequest;
import four_tential.potential.presentation.auth.model.request.SocialLinkConfirmRequest;
import four_tential.potential.presentation.auth.model.request.SocialLinkRequest;
import four_tential.potential.presentation.auth.model.response.LoginResponse;
import four_tential.potential.presentation.auth.model.response.OAuthLinkExchangeResponse;
import four_tential.potential.presentation.auth.model.response.OAuthLoginExchangeResponse;
import four_tential.potential.presentation.auth.model.response.RefreshResponse;
import four_tential.potential.presentation.auth.model.response.SignUpResponse;
import four_tential.potential.presentation.auth.model.response.SocialLinkConfirmResponse;
import four_tential.potential.presentation.auth.model.response.SocialLinkResponse;

import java.util.Optional;
import java.util.UUID;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthFacade authFacade;

    @Mock
    private OAuthRedirectTicketRepository oauthTicketRepository;

    @Mock
    private HttpServletResponse httpServletResponse;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authController, "refreshTokenExpire", 1_209_600_000L); // 14일
        ReflectionTestUtils.setField(authController, "cookieSecure", false);
    }

    @Test
    @DisplayName("회원가입 성공 - 201 Created 및 회원 정보 반환")
    void signUp_success() {
        var request = SignUpRequestFixture.defaultRequest();
        var serviceResponse = new SignUpResponse(request.email(), request.name(), "ROLE_STUDENT", "ACTIVE");
        given(authFacade.signUp(request)).willReturn(serviceResponse);

        ResponseEntity<BaseResponse<SignUpResponse>> response = authController.signUp(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assert response.getBody() != null;
        assertThat(response.getBody().data().email()).isEqualTo(request.email());
        assertThat(response.getBody().data().name()).isEqualTo(request.name());
        assertThat(response.getBody().data().role()).isEqualTo("ROLE_STUDENT");
    }

    @Test
    @DisplayName("로그인 성공 - 200 OK, accessToken Body 반환")
    void login_success() {
        LoginRequest request = LoginRequestFixture.defaultRequest();
        given(authFacade.login(request)).willReturn(new LoginResult("accessToken", "refreshToken", false));

        ResponseEntity<BaseResponse<LoginResponse>> response = authController.login(request, httpServletResponse);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        assert response.getBody() != null;
        assertThat(response.getBody().data().accessToken()).isEqualTo("accessToken");
    }

    @Test
    @DisplayName("로그인 성공 - refreshToken을 HttpOnly Cookie로 설정")
    void login_setsRefreshTokenCookie() {
        LoginRequest request = LoginRequestFixture.defaultRequest();
        given(authFacade.login(request)).willReturn(new LoginResult("accessToken", "refreshToken", false));

        authController.login(request, httpServletResponse);

        verify(httpServletResponse).addHeader(eq(HttpHeaders.SET_COOKIE), contains("refreshToken=refreshToken"));
    }

    @Test
    @DisplayName("로그인 성공 - 온보딩 완료 회원은 hasOnboarding true 반환")
    void login_withOnboarding() {
        LoginRequest request = LoginRequestFixture.defaultRequest();
        given(authFacade.login(request)).willReturn(new LoginResult("accessToken", "refreshToken", true));

        ResponseEntity<BaseResponse<LoginResponse>> response = authController.login(request, httpServletResponse);

        assert response.getBody() != null;
        assertThat(response.getBody().data().hasOnboarding()).isTrue();
    }

    @Test
    @DisplayName("로그인 성공 - 온보딩 미완료 회원은 hasOnboarding false 반환")
    void login_withoutOnboarding() {
        LoginRequest request = LoginRequestFixture.defaultRequest();
        given(authFacade.login(request)).willReturn(new LoginResult("accessToken", "refreshToken", false));

        ResponseEntity<BaseResponse<LoginResponse>> response = authController.login(request, httpServletResponse);

        assert response.getBody() != null;
        assertThat(response.getBody().data().hasOnboarding()).isFalse();
    }

    @Test
    @DisplayName("토큰 재발급 성공 - 200 OK, 새 accessToken Body 반환")
    void refresh_success() {
        given(authFacade.refresh("oldRefreshToken")).willReturn(new RefreshResult("newAccessToken", "newRefreshToken"));

        ResponseEntity<BaseResponse<RefreshResponse>> response = authController.refresh("oldRefreshToken", httpServletResponse);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        assert response.getBody() != null;
        assertThat(response.getBody().data().accessToken()).isEqualTo("newAccessToken");
    }

    @Test
    @DisplayName("토큰 재발급 성공 - 새 refreshToken을 HttpOnly Cookie로 교체")
    void refresh_setsNewCookie() {
        given(authFacade.refresh("oldRefreshToken")).willReturn(new RefreshResult("newAccessToken", "newRefreshToken"));

        authController.refresh("oldRefreshToken", httpServletResponse);

        verify(httpServletResponse).addHeader(eq(HttpHeaders.SET_COOKIE), contains("refreshToken=newRefreshToken"));
    }

    @Test
    @DisplayName("쿠키 없이 재발급 요청 - ServiceErrorException 발생")
    void refresh_noToken() {
        assertThatThrownBy(() -> authController.refresh(null, httpServletResponse))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("인증 정보가 비어있습니다");
    }

    @Test
    @DisplayName("로그아웃 성공 - 200 OK 반환")
    void logOut_success() {
        ResponseEntity<BaseResponse<Void>> response = authController.logOut("Bearer validAccessToken", httpServletResponse);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(authFacade).logOut("validAccessToken");
    }

    @Test
    @DisplayName("로그아웃 성공 - refreshToken 쿠키 만료 헤더 반환")
    void logOut_clearsRefreshTokenCookie() {
        authController.logOut("Bearer validAccessToken", httpServletResponse);

        verify(httpServletResponse).addHeader(eq(HttpHeaders.SET_COOKIE), contains("refreshToken=;"));
    }

    @Test
    @DisplayName("잘못된 Authorization 헤더로 로그아웃 - ServiceErrorException 발생")
    void logOut_invalidHeader() {
        assertThatThrownBy(() -> authController.logOut("InvalidHeader", httpServletResponse))
                .isInstanceOf(ServiceErrorException.class);
    }

    @Test
    @DisplayName("소셜 계정 연동 성공 - 200 OK 및 provider/email 반환")
    void linkSocialAccount_success() {
        UUID memberId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "user@example.com", "ROLE_STUDENT");
        SocialLinkRequest request = new SocialLinkRequest("rawPassword", "code-123", "https://app/cb");
        OAuth2UserAttributes attributes = new OAuth2UserAttributes(SocialProvider.KAKAO, "kakao-1", "user@kakao.com", "홍길동");

        given(authFacade.linkSocialAccount(memberId, SocialProvider.KAKAO, "rawPassword", "code-123", "https://app/cb"))
                .willReturn(attributes);

        ResponseEntity<BaseResponse<SocialLinkResponse>> response =
                authController.linkSocialAccount(SocialProvider.KAKAO, principal, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assert response.getBody() != null;
        assertThat(response.getBody().data().provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(response.getBody().data().email()).isEqualTo("user@kakao.com");
    }

    @Test
    @DisplayName("소셜 계정 연동 해제 성공 - 200 OK 반환 및 Facade 위임")
    void unlinkSocialAccount_success() {
        UUID memberId = UUID.randomUUID();
        MemberPrincipal principal = new MemberPrincipal(memberId, "user@example.com", "ROLE_STUDENT");

        ResponseEntity<BaseResponse<Void>> response =
                authController.unlinkSocialAccount(SocialProvider.GOOGLE, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(authFacade).unlinkSocialAccount(memberId, SocialProvider.GOOGLE);
    }

    @Test
    @DisplayName("소셜 연동 챌린지 확인 성공 - accessToken/연동 정보 반환 + refreshToken 쿠키")
    void confirmSocialLink_success() {
        SocialLinkConfirmRequest request = new SocialLinkConfirmRequest("challenge-1", "rawPassword");
        SocialLinkConfirmResult result = new SocialLinkConfirmResult(
                "access-token", "refresh-token", true, false, SocialProvider.KAKAO, "user@example.com"
        );
        given(authFacade.confirmSocialLink("challenge-1", "rawPassword")).willReturn(result);

        ResponseEntity<BaseResponse<SocialLinkConfirmResponse>> response =
                authController.confirmSocialLink(request, httpServletResponse);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assert response.getBody() != null;
        assertThat(response.getBody().data().accessToken()).isEqualTo("access-token");
        assertThat(response.getBody().data().hasOnboarding()).isTrue();
        assertThat(response.getBody().data().requiresPhoneSetup()).isFalse();
        assertThat(response.getBody().data().linkedProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(response.getBody().data().email()).isEqualTo("user@example.com");

        verify(httpServletResponse).addHeader(eq(HttpHeaders.SET_COOKIE), contains("refreshToken=refresh-token"));
    }

    @Test
    @DisplayName("OAuth 로그인 티켓 교환 성공 - accessToken/온보딩 정보 반환")
    void exchangeLoginTicket_success() {
        OAuthTicketExchangeRequest request = new OAuthTicketExchangeRequest("ticket-1");
        OAuthLoginTicketData data = new OAuthLoginTicketData("access-token", true, false);
        given(oauthTicketRepository.consumeLogin("ticket-1")).willReturn(Optional.of(data));

        ResponseEntity<BaseResponse<OAuthLoginExchangeResponse>> response =
                authController.exchangeLoginTicket(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assert response.getBody() != null;
        assertThat(response.getBody().data().accessToken()).isEqualTo("access-token");
        assertThat(response.getBody().data().hasOnboarding()).isTrue();
        assertThat(response.getBody().data().requiresPhoneSetup()).isFalse();
    }

    @Test
    @DisplayName("OAuth 로그인 티켓 교환 - 만료/유효하지 않으면 ERR_OAUTH_TICKET_INVALID")
    void exchangeLoginTicket_invalid() {
        OAuthTicketExchangeRequest request = new OAuthTicketExchangeRequest("expired");
        given(oauthTicketRepository.consumeLogin("expired")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authController.exchangeLoginTicket(request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("유효하지 않거나 만료된 티켓입니다");
    }

    @Test
    @DisplayName("OAuth 연동 티켓 교환 성공 - challengeToken/email/provider 반환")
    void exchangeLinkTicket_success() {
        OAuthTicketExchangeRequest request = new OAuthTicketExchangeRequest("link-ticket-1");
        OAuthLinkTicketData data = new OAuthLinkTicketData("challenge-1", "user@example.com", SocialProvider.KAKAO);
        given(oauthTicketRepository.consumeLink("link-ticket-1")).willReturn(Optional.of(data));

        ResponseEntity<BaseResponse<OAuthLinkExchangeResponse>> response =
                authController.exchangeLinkTicket(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assert response.getBody() != null;
        assertThat(response.getBody().data().challengeToken()).isEqualTo("challenge-1");
        assertThat(response.getBody().data().email()).isEqualTo("user@example.com");
        assertThat(response.getBody().data().provider()).isEqualTo(SocialProvider.KAKAO);
    }

    @Test
    @DisplayName("OAuth 연동 티켓 교환 - 만료/유효하지 않으면 ERR_OAUTH_TICKET_INVALID")
    void exchangeLinkTicket_invalid() {
        OAuthTicketExchangeRequest request = new OAuthTicketExchangeRequest("nope");
        given(oauthTicketRepository.consumeLink("nope")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authController.exchangeLinkTicket(request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("유효하지 않거나 만료된 티켓입니다");
    }
}
