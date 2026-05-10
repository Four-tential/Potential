package four_tential.potential.application.auth;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.member.fixture.MemberFixture;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.member.member.MemberRole;
import four_tential.potential.domain.member.member.MemberStatus;
import four_tential.potential.domain.member.social.SocialProvider;
import four_tential.potential.infra.jwt.JwtRepository;
import four_tential.potential.infra.jwt.JwtUtil;
import four_tential.potential.infra.oauth2.OAuth2TokenExchangeClient;
import four_tential.potential.infra.oauth2.OAuth2UserAttributes;
import four_tential.potential.presentation.auth.fixture.LoginRequestFixture;
import four_tential.potential.presentation.auth.fixture.SignUpRequestFixture;
import four_tential.potential.presentation.auth.model.LoginResult;
import four_tential.potential.presentation.auth.model.RefreshResult;
import four_tential.potential.presentation.auth.model.SocialLinkConfirmResult;
import four_tential.potential.presentation.auth.model.request.LoginRequest;
import four_tential.potential.presentation.auth.model.request.SignUpRequest;
import four_tential.potential.presentation.auth.model.response.SignUpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthFacadeTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private JwtRepository jwtRepository;
    @Mock
    private AuthService authService;
    @Mock
    private SocialAuthService socialAuthService;
    @Mock
    private OAuth2TokenExchangeClient oAuth2TokenExchangeClient;
    @Mock
    private SocialLinkChallengeRepository socialLinkChallengeRepository;

    @InjectMocks
    private AuthFacade authFacade;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authFacade, "refreshTokenExpire", 1_209_600_000L); // 14일(ms)
    }

    @Test
    @DisplayName("회원가입 성공 - 응답 값 및 저장 검증")
    void signUp() {
        SignUpRequest request = SignUpRequestFixture.defaultRequest();
        given(memberRepository.existsByEmail(request.email())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encodedPassword");

        SignUpResponse expectedResponse = new SignUpResponse(
                request.email(), request.name(),
                MemberRole.ROLE_STUDENT.name(), MemberStatus.ACTIVE.name()
        );
        given(authService.saveMember(request, "encodedPassword")).willReturn(expectedResponse);

        SignUpResponse response = authFacade.signUp(request);

        assertThat(response.email()).isEqualTo(request.email());
        assertThat(response.name()).isEqualTo(request.name());
        assertThat(response.role()).isEqualTo(MemberRole.ROLE_STUDENT.name());
        assertThat(response.status()).isEqualTo(MemberStatus.ACTIVE.name());

        verify(authService).saveMember(request, "encodedPassword");
    }

    @Test
    @DisplayName("이메일 중복 - ServiceErrorException 발생")
    void signUpDuplicateEmail() {
        SignUpRequest request = SignUpRequestFixture.defaultRequest();
        given(memberRepository.existsByEmail(request.email())).willReturn(true);

        assertThatThrownBy(() -> authFacade.signUp(request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("이미 사용 중인 이메일 입니다");
        verify(authService, never()).saveMember(any(), any());
    }


    @Test
    @DisplayName("로그인 성공 - 온보딩 완료 회원은 hasOnboarding true 반환")
    void login_withOnboarding() {
        LoginRequest request = LoginRequestFixture.defaultRequest();
        Member member = MemberFixture.memberWithOnboarding();
        given(memberRepository.findByEmail(request.email())).willReturn(Optional.of(member));
        given(passwordEncoder.matches(request.password(), member.getPassword())).willReturn(true);
        given(jwtUtil.createAccessToken(any(), any(), any())).willReturn("accessToken");
        given(jwtUtil.createRefreshToken(any())).willReturn("refreshToken");

        LoginResult result = authFacade.login(request);

        assertThat(result.accessToken()).isEqualTo("accessToken");
        assertThat(result.refreshToken()).isEqualTo("refreshToken");
        assertThat(result.hasOnboarding()).isTrue();
        verify(jwtRepository).saveRefreshToken(eq(member.getEmail()), eq("refreshToken"), anyLong());
    }

    @Test
    @DisplayName("로그인 성공 - 온보딩 미완료 회원은 hasOnboarding false 반환")
    void login_withoutOnboarding() {
        LoginRequest request = LoginRequestFixture.defaultRequest();
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findByEmail(request.email())).willReturn(Optional.of(member));
        given(passwordEncoder.matches(request.password(), member.getPassword())).willReturn(true);
        given(jwtUtil.createAccessToken(any(), any(), any())).willReturn("accessToken");
        given(jwtUtil.createRefreshToken(any())).willReturn("refreshToken");

        LoginResult result = authFacade.login(request);

        assertThat(result.hasOnboarding()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 이메일로 로그인 - ServiceErrorException 발생")
    void login_memberNotFound() {
        LoginRequest request = LoginRequestFixture.defaultRequest();
        given(memberRepository.findByEmail(request.email())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authFacade.login(request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("아이디와 비밀번호를 확인하시기 바랍니다");
    }

    @Test
    @DisplayName("비밀번호 불일치로 로그인 - ServiceErrorException 발생")
    void login_wrongPassword() {
        LoginRequest request = LoginRequestFixture.defaultRequest();
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findByEmail(request.email())).willReturn(Optional.of(member));
        given(passwordEncoder.matches(request.password(), member.getPassword())).willReturn(false);

        assertThatThrownBy(() -> authFacade.login(request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("아이디와 비밀번호를 확인하시기 바랍니다");
    }

    @Test
    @DisplayName("탈퇴 회원 로그인 - ServiceErrorException 발생")
    void login_withdrawalMember() {
        LoginRequest request = LoginRequestFixture.defaultRequest();
        Member member = MemberFixture.defaultMember();
        member.withdraw();
        given(memberRepository.findByEmail(request.email())).willReturn(Optional.of(member));
        given(passwordEncoder.matches(request.password(), member.getPassword())).willReturn(true);

        assertThatThrownBy(() -> authFacade.login(request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("아이디와 비밀번호를 확인하시기 바랍니다");
    }

    @Test
    @DisplayName("정지 회원 로그인 - ServiceErrorException 발생")
    void login_suspendedMember() {
        LoginRequest request = LoginRequestFixture.defaultRequest();
        Member member = MemberFixture.defaultMember();
        member.suspend();
        given(memberRepository.findByEmail(request.email())).willReturn(Optional.of(member));
        given(passwordEncoder.matches(request.password(), member.getPassword())).willReturn(true);

        assertThatThrownBy(() -> authFacade.login(request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("정지된 회원입니다, 관리자에게 문의 바랍니다");
    }

    @Test
    @DisplayName("토큰 재발급 성공 - 새 토큰 반환 및 Redis 갱신")
    void refresh() {
        String oldRefreshToken = "validRefreshToken";
        Member member = MemberFixture.defaultMember();
        given(jwtUtil.validateToken(oldRefreshToken)).willReturn(true);
        given(jwtUtil.extractSubject(oldRefreshToken)).willReturn(MemberFixture.DEFAULT_EMAIL);
        given(jwtRepository.getAndDeleteRefreshToken(MemberFixture.DEFAULT_EMAIL)).willReturn(oldRefreshToken);
        given(memberRepository.findByEmail(MemberFixture.DEFAULT_EMAIL)).willReturn(Optional.of(member));
        given(jwtUtil.createAccessToken(any(), any(), any())).willReturn("newAccessToken");
        given(jwtUtil.createRefreshToken(any())).willReturn("newRefreshToken");

        RefreshResult result = authFacade.refresh(oldRefreshToken);

        assertThat(result.newAccessToken()).isEqualTo("newAccessToken");
        assertThat(result.newRefreshToken()).isEqualTo("newRefreshToken");
        verify(jwtRepository).saveRefreshToken(eq(MemberFixture.DEFAULT_EMAIL), eq("newRefreshToken"), anyLong());
    }

    @Test
    @DisplayName("유효하지 않은 리프레시 토큰 - ServiceErrorException 발생")
    void refresh_invalidToken() {
        String invalidToken = "invalidToken";
        given(jwtUtil.validateToken(invalidToken)).willReturn(false);

        assertThatThrownBy(() -> authFacade.refresh(invalidToken))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("잘못된 인증 정보입니다, 다시 로그인 하시기 바랍니다");
    }

    @Test
    @DisplayName("Redis에 토큰 없음 (로그아웃 상태) - ServiceErrorException 발생")
    void refresh_noTokenInRedis() {
        String refreshToken = "someToken";
        given(jwtUtil.validateToken(refreshToken)).willReturn(true);
        given(jwtUtil.extractSubject(refreshToken)).willReturn(MemberFixture.DEFAULT_EMAIL);
        given(jwtRepository.getAndDeleteRefreshToken(MemberFixture.DEFAULT_EMAIL)).willReturn(null);

        assertThatThrownBy(() -> authFacade.refresh(refreshToken))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("잘못된 인증 정보입니다, 다시 로그인 하시기 바랍니다");
    }

    @Test
    @DisplayName("Redis 저장 토큰과 불일치 (의심 대상) - Redis 삭제 후 ServiceErrorException 발생")
    void refresh_tokenMismatch() {
        String stolenToken = "stolenToken";
        given(jwtUtil.validateToken(stolenToken)).willReturn(true);
        given(jwtUtil.extractSubject(stolenToken)).willReturn(MemberFixture.DEFAULT_EMAIL);
        given(jwtRepository.getAndDeleteRefreshToken(MemberFixture.DEFAULT_EMAIL)).willReturn("differentToken");

        assertThatThrownBy(() -> authFacade.refresh(stolenToken))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("잘못된 인증 정보입니다, 다시 로그인 하시기 바랍니다");

        // GETDEL로 이미 원자적 삭제되므로 deleteRefreshToken은 별도 호출하지 않음
        verify(jwtRepository, never()).deleteRefreshToken(any());
    }

    @Test
    @DisplayName("재발급 시 비활성 회원 - ServiceErrorException 발생")
    void refresh_inactiveMember() {
        String refreshToken = "validRefreshToken";
        Member member = MemberFixture.defaultMember();
        member.suspend();
        given(jwtUtil.validateToken(refreshToken)).willReturn(true);
        given(jwtUtil.extractSubject(refreshToken)).willReturn(MemberFixture.DEFAULT_EMAIL);
        given(jwtRepository.getAndDeleteRefreshToken(MemberFixture.DEFAULT_EMAIL)).willReturn(refreshToken);
        given(memberRepository.findByEmail(MemberFixture.DEFAULT_EMAIL)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> authFacade.refresh(refreshToken))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("잘못된 인증 정보입니다, 다시 로그인 하시기 바랍니다");
    }

    @Test
    @DisplayName("로그아웃 성공 - 유효한 토큰: refreshToken 삭제 및 accessToken 블랙리스트 등록")
    void logOut() {
        String accessToken = "validAccessToken";
        given(jwtUtil.validateToken(accessToken)).willReturn(true);
        given(jwtUtil.extractSubjectAllowExpired(accessToken)).willReturn(MemberFixture.DEFAULT_EMAIL);
        given(jwtUtil.getRemainingTime(accessToken)).willReturn(3600000L);

        authFacade.logOut(accessToken);

        verify(jwtRepository).deleteRefreshToken(MemberFixture.DEFAULT_EMAIL);
        verify(jwtRepository).addBlacklist(accessToken, 3600000L);
    }

    @Test
    @DisplayName("로그아웃 성공 - 만료된 토큰: refreshToken만 삭제하고 블랙리스트 미등록")
    void logOut_expiredToken() {
        String expiredToken = "expiredAccessToken";
        given(jwtUtil.validateToken(expiredToken)).willReturn(false);
        given(jwtUtil.isExpiredToken(expiredToken)).willReturn(true);
        given(jwtUtil.extractSubjectAllowExpired(expiredToken)).willReturn(MemberFixture.DEFAULT_EMAIL);

        authFacade.logOut(expiredToken);

        verify(jwtRepository).deleteRefreshToken(MemberFixture.DEFAULT_EMAIL);
        verify(jwtRepository, never()).addBlacklist(any(), anyLong());
    }

    @Test
    @DisplayName("위변조된 토큰으로 로그아웃 - ServiceErrorException 발생")
    void logOut_invalidToken() {
        String invalidToken = "tamperedAccessToken";
        given(jwtUtil.validateToken(invalidToken)).willReturn(false);
        given(jwtUtil.isExpiredToken(invalidToken)).willReturn(false);

        assertThatThrownBy(() -> authFacade.logOut(invalidToken))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("잘못된 인증 정보입니다, 다시 로그인 하시기 바랍니다");

        verify(jwtRepository, never()).deleteRefreshToken(any());
        verify(jwtRepository, never()).addBlacklist(any(), anyLong());
    }

    @Test
    @DisplayName("소셜 계정 수동 연동 성공 - 비밀번호 검증 후 SocialAuthService 위임")
    void linkSocialAccount_success() {
        UUID memberId = UUID.randomUUID();
        Member member = MemberFixture.defaultMember();
        OAuth2UserAttributes attributes = new OAuth2UserAttributes(SocialProvider.KAKAO, "kakao-1", "user@kakao.com", "홍길동");

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(passwordEncoder.matches("rawPassword", member.getPassword())).willReturn(true);
        given(oAuth2TokenExchangeClient.exchangeAndFetch("kakao", "code-123", "https://app/cb"))
                .willReturn(attributes);

        OAuth2UserAttributes result = authFacade.linkSocialAccount(memberId, SocialProvider.KAKAO, "rawPassword", "code-123", "https://app/cb");

        assertThat(result).isEqualTo(attributes);
        verify(socialAuthService).linkExistingMember(memberId, attributes);
    }

    @Test
    @DisplayName("소셜 계정 수동 연동 - 비밀번호 미설정 회원이면 ERR_NO_PASSWORD_SET")
    void linkSocialAccount_noPasswordSet() {
        UUID memberId = UUID.randomUUID();
        Member socialOnlyMember = Member.registerSocial("social@example.com", "홍길동");

        given(memberRepository.findById(memberId)).willReturn(Optional.of(socialOnlyMember));

        assertThatThrownBy(() -> authFacade.linkSocialAccount(memberId, SocialProvider.KAKAO, "any", "code", "uri"))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("비밀번호가 설정되지 않은 계정입니다");

        verify(oAuth2TokenExchangeClient, never()).exchangeAndFetch(any(), any(), any());
        verify(socialAuthService, never()).linkExistingMember(any(), any());
    }

    @Test
    @DisplayName("소셜 계정 수동 연동 - 비밀번호 불일치 시 ERR_WRONG_PASSWORD")
    void linkSocialAccount_wrongPassword() {
        UUID memberId = UUID.randomUUID();
        Member member = MemberFixture.defaultMember();

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(passwordEncoder.matches("wrong", member.getPassword())).willReturn(false);

        assertThatThrownBy(() -> authFacade.linkSocialAccount(memberId, SocialProvider.KAKAO, "wrong", "code", "uri"))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("비밀번호가 올바르지 않습니다");

        verify(oAuth2TokenExchangeClient, never()).exchangeAndFetch(any(), any(), any());
        verify(socialAuthService, never()).linkExistingMember(any(), any());
    }

    @Test
    @DisplayName("소셜 계정 수동 연동 - provider 가 path 와 다르면 ERR_SOCIAL_PROVIDER_NOT_SUPPORTED")
    void linkSocialAccount_providerMismatch() {
        UUID memberId = UUID.randomUUID();
        Member member = MemberFixture.defaultMember();
        OAuth2UserAttributes returnedAttrs = new OAuth2UserAttributes(SocialProvider.GOOGLE, "google-1", "user@google.com", "홍길동");

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(passwordEncoder.matches("rawPassword", member.getPassword())).willReturn(true);
        given(oAuth2TokenExchangeClient.exchangeAndFetch("kakao", "code", "uri")).willReturn(returnedAttrs);

        assertThatThrownBy(() -> authFacade.linkSocialAccount(memberId, SocialProvider.KAKAO, "rawPassword", "code", "uri"))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("지원하지 않는 소셜 로그인입니다");

        verify(socialAuthService, never()).linkExistingMember(any(), any());
    }

    @Test
    @DisplayName("소셜 계정 연동 해제 - SocialAuthService 위임")
    void unlinkSocialAccount_delegates() {
        UUID memberId = UUID.randomUUID();

        authFacade.unlinkSocialAccount(memberId, SocialProvider.GOOGLE);

        verify(socialAuthService).unlinkSocialAccount(memberId, SocialProvider.GOOGLE);
    }

    @Test
    @DisplayName("소셜 연동 챌린지 확인 - 비밀번호 일치 시 연동 + 토큰 발급")
    void confirmSocialLink_success() {
        Member member = MemberFixture.defaultMember();
        SocialLinkChallengeData data = new SocialLinkChallengeData(
                SocialProvider.KAKAO, "kakao-1", member.getEmail(), "홍길동"
        );

        given(socialLinkChallengeRepository.peek("ch-1")).willReturn(Optional.of(data));
        given(memberRepository.findByEmail(member.getEmail())).willReturn(Optional.of(member));
        given(passwordEncoder.matches("rawPwd", member.getPassword())).willReturn(true);
        given(jwtUtil.createAccessToken(any(), any(), any())).willReturn("at");
        given(jwtUtil.createRefreshToken(any())).willReturn("rt");

        SocialLinkConfirmResult result = authFacade.confirmSocialLink("ch-1", "rawPwd");

        assertThat(result.accessToken()).isEqualTo("at");
        assertThat(result.refreshToken()).isEqualTo("rt");
        assertThat(result.linkedProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(result.email()).isEqualTo(member.getEmail());

        verify(socialLinkChallengeRepository).invalidate("ch-1");
        verify(socialAuthService).linkExistingMember(eq(member.getId()), any(OAuth2UserAttributes.class));
        verify(jwtRepository).saveRefreshToken(eq(member.getEmail()), eq("rt"), anyLong());
    }

    @Test
    @DisplayName("소셜 연동 챌린지 확인 - 챌린지 만료/시도 초과 시 ERR_INVALID_AUTHORIZE")
    void confirmSocialLink_challengeMissing() {
        given(socialLinkChallengeRepository.peek("ch-x")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authFacade.confirmSocialLink("ch-x", "any"))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("잘못된 인증 정보입니다, 다시 로그인 하시기 바랍니다");

        verify(socialAuthService, never()).linkExistingMember(any(), any());
    }

    @Test
    @DisplayName("소셜 연동 챌린지 확인 - 비밀번호 불일치 시 ERR_WRONG_PASSWORD, 챌린지 invalidate 안 함")
    void confirmSocialLink_wrongPassword() {
        Member member = MemberFixture.defaultMember();
        SocialLinkChallengeData data = new SocialLinkChallengeData(
                SocialProvider.KAKAO, "kakao-1", member.getEmail(), "홍길동"
        );

        given(socialLinkChallengeRepository.peek("ch-2")).willReturn(Optional.of(data));
        given(memberRepository.findByEmail(member.getEmail())).willReturn(Optional.of(member));
        given(passwordEncoder.matches("wrong", member.getPassword())).willReturn(false);

        assertThatThrownBy(() -> authFacade.confirmSocialLink("ch-2", "wrong"))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("비밀번호가 올바르지 않습니다");

        verify(socialLinkChallengeRepository, never()).invalidate(any());
        verify(socialAuthService, never()).linkExistingMember(any(), any());
    }

    @Test
    @DisplayName("소셜 연동 챌린지 확인 - 비밀번호 미설정 회원이면 ERR_NO_PASSWORD_SET")
    void confirmSocialLink_noPasswordSet() {
        Member socialOnly = Member.registerSocial("social@example.com", "유저");
        SocialLinkChallengeData data = new SocialLinkChallengeData(
                SocialProvider.GOOGLE, "google-1", socialOnly.getEmail(), "유저"
        );

        given(socialLinkChallengeRepository.peek("ch-3")).willReturn(Optional.of(data));
        given(memberRepository.findByEmail(socialOnly.getEmail())).willReturn(Optional.of(socialOnly));

        assertThatThrownBy(() -> authFacade.confirmSocialLink("ch-3", "anything"))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("비밀번호가 설정되지 않은 계정입니다");
    }

    @Test
    @DisplayName("소셜 연동 챌린지 확인 - 챌린지 데이터 email 누락 시 invalidate + ERR_INVALID_AUTHORIZE")
    void confirmSocialLink_blankEmail() {
        SocialLinkChallengeData data = new SocialLinkChallengeData(
                SocialProvider.KAKAO, "kakao-1", "", "홍길동"
        );
        given(socialLinkChallengeRepository.peek("ch-blank")).willReturn(Optional.of(data));

        assertThatThrownBy(() -> authFacade.confirmSocialLink("ch-blank", "any"))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("잘못된 인증 정보입니다, 다시 로그인 하시기 바랍니다");

        verify(socialLinkChallengeRepository).invalidate("ch-blank");
        verify(memberRepository, never()).findByEmail(any());
    }

    @Test
    @DisplayName("소셜 연동 챌린지 확인 - 탈퇴 회원이면 ERR_WRONG_LOGIN")
    void confirmSocialLink_withdrawn() {
        Member member = MemberFixture.defaultMember();
        member.withdraw();
        SocialLinkChallengeData data = new SocialLinkChallengeData(
                SocialProvider.KAKAO, "kakao-1", member.getEmail(), "홍길동"
        );

        given(socialLinkChallengeRepository.peek("ch-w")).willReturn(Optional.of(data));
        given(memberRepository.findByEmail(member.getEmail())).willReturn(Optional.of(member));

        assertThatThrownBy(() -> authFacade.confirmSocialLink("ch-w", "any"))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("아이디와 비밀번호를 확인하시기 바랍니다");
    }

    @Test
    @DisplayName("소셜 연동 챌린지 확인 - 정지 회원이면 ERR_SUSPENDED")
    void confirmSocialLink_suspended() {
        Member member = MemberFixture.defaultMember();
        member.suspend();
        SocialLinkChallengeData data = new SocialLinkChallengeData(
                SocialProvider.KAKAO, "kakao-1", member.getEmail(), "홍길동"
        );

        given(socialLinkChallengeRepository.peek("ch-s")).willReturn(Optional.of(data));
        given(memberRepository.findByEmail(member.getEmail())).willReturn(Optional.of(member));

        assertThatThrownBy(() -> authFacade.confirmSocialLink("ch-s", "any"))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("정지된 회원입니다, 관리자에게 문의 바랍니다");
    }
}
