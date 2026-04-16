package four_tential.potential.application.auth;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.member.fixture.MemberFixture;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.member.member.MemberRole;
import four_tential.potential.domain.member.member.MemberStatus;
import four_tential.potential.infra.jwt.JwtRepository;
import four_tential.potential.infra.jwt.JwtUtil;
import four_tential.potential.presentation.auth.fixture.LoginRequestFixture;
import four_tential.potential.presentation.auth.fixture.SignUpRequestFixture;
import four_tential.potential.presentation.auth.model.LoginResult;
import four_tential.potential.presentation.auth.model.RefreshResult;
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

import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private JwtRepository jwtRepository;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpire", 1_209_600_000L); // 14일(ms)
    }

    @Test
    @DisplayName("회원가입 성공 - 응답 값 및 저장 검증")
    void signUp() {
        SignUpRequest request = SignUpRequestFixture.defaultRequest();
        given(memberRepository.existsByEmail(request.email())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encodedPassword");

        SignUpResponse response = authService.signUp(request);

        assertThat(response.email()).isEqualTo(request.email());
        assertThat(response.name()).isEqualTo(request.name());
        assertThat(response.role()).isEqualTo(MemberRole.ROLE_STUDENT.name());
        assertThat(response.status()).isEqualTo(MemberStatus.ACTIVE.name());
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    @DisplayName("이메일 중복 - ServiceErrorException 발생")
    void signUpDuplicateEmail() {
        SignUpRequest request = SignUpRequestFixture.defaultRequest();
        given(memberRepository.existsByEmail(request.email())).willReturn(true);

        assertThatThrownBy(() -> authService.signUp(request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("이미 사용 중인 이메일 입니다");
        verify(memberRepository, never()).save(any(Member.class));
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

        LoginResult result = authService.login(request);

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

        LoginResult result = authService.login(request);

        assertThat(result.hasOnboarding()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 이메일로 로그인 - ServiceErrorException 발생")
    void login_memberNotFound() {
        LoginRequest request = LoginRequestFixture.defaultRequest();
        given(memberRepository.findByEmail(request.email())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
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

        assertThatThrownBy(() -> authService.login(request))
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

        assertThatThrownBy(() -> authService.login(request))
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

        assertThatThrownBy(() -> authService.login(request))
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

        RefreshResult result = authService.refresh(oldRefreshToken);

        assertThat(result.newAccessToken()).isEqualTo("newAccessToken");
        assertThat(result.newRefreshToken()).isEqualTo("newRefreshToken");
        verify(jwtRepository).saveRefreshToken(eq(MemberFixture.DEFAULT_EMAIL), eq("newRefreshToken"), anyLong());
    }

    @Test
    @DisplayName("유효하지 않은 리프레시 토큰 - ServiceErrorException 발생")
    void refresh_invalidToken() {
        String invalidToken = "invalidToken";
        given(jwtUtil.validateToken(invalidToken)).willReturn(false);

        assertThatThrownBy(() -> authService.refresh(invalidToken))
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

        assertThatThrownBy(() -> authService.refresh(refreshToken))
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

        assertThatThrownBy(() -> authService.refresh(stolenToken))
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

        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("잘못된 인증 정보입니다, 다시 로그인 하시기 바랍니다");
    }

    @Test
    @DisplayName("로그아웃 성공 - refreshToken 삭제 및 accessToken 블랙리스트 등록")
    void logOut() {
        String accessToken = "validAccessToken";
        given(jwtUtil.validateToken(accessToken)).willReturn(true);
        given(jwtUtil.extractSubject(accessToken)).willReturn(MemberFixture.DEFAULT_EMAIL);
        given(jwtUtil.getRemainingTime(accessToken)).willReturn(3600000L);

        authService.logOut(accessToken);

        verify(jwtRepository).deleteRefreshToken(MemberFixture.DEFAULT_EMAIL);
        verify(jwtRepository).addBlacklist(accessToken, 3600000L);
    }

    @Test
    @DisplayName("유효하지 않은 토큰으로 로그아웃 - ServiceErrorException 발생")
    void logOut_invalidToken() {
        String invalidToken = "invalidAccessToken";
        given(jwtUtil.validateToken(invalidToken)).willReturn(false);

        assertThatThrownBy(() -> authService.logOut(invalidToken))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("잘못된 인증 정보입니다, 다시 로그인 하시기 바랍니다");

        verify(jwtRepository, never()).deleteRefreshToken(any());
        verify(jwtRepository, never()).addBlacklist(any(), anyLong());
    }
}
