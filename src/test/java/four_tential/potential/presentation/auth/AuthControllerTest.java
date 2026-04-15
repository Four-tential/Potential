package four_tential.potential.presentation.auth;

import four_tential.potential.application.auth.AuthService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.presentation.auth.fixture.LoginRequestFixture;
import four_tential.potential.presentation.auth.fixture.SignUpRequestFixture;
import four_tential.potential.presentation.auth.model.LoginResult;
import four_tential.potential.presentation.auth.model.RefreshResult;
import four_tential.potential.presentation.auth.model.request.LoginRequest;
import four_tential.potential.presentation.auth.model.response.LoginResponse;
import four_tential.potential.presentation.auth.model.response.RefreshResponse;
import four_tential.potential.presentation.auth.model.response.SignUpResponse;
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
    private AuthService authService;

    @Mock
    private HttpServletResponse httpServletResponse;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authController, "refreshTokenExpire", 1_209_600_000L); // 14일
    }

    @Test
    @DisplayName("회원가입 성공 - 201 Created 및 회원 정보 반환")
    void signUp_success() {
        var request = SignUpRequestFixture.defaultRequest();
        var serviceResponse = new SignUpResponse(request.email(), request.name(), "ROLE_STUDENT", "ACTIVE");
        given(authService.signUp(request)).willReturn(serviceResponse);

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
        given(authService.login(request)).willReturn(new LoginResult("accessToken", "refreshToken", false));

        ResponseEntity<BaseResponse<LoginResponse>> response = authController.login(request, httpServletResponse);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        assert response.getBody() != null;
        assertThat(response.getBody().data().accessToken()).isEqualTo("accessToken");
    }

    @Test
    @DisplayName("로그인 성공 - refreshToken을 HttpOnly Cookie로 설정")
    void login_setsRefreshTokenCookie() {
        LoginRequest request = LoginRequestFixture.defaultRequest();
        given(authService.login(request)).willReturn(new LoginResult("accessToken", "refreshToken", false));

        authController.login(request, httpServletResponse);

        verify(httpServletResponse).addHeader(eq(HttpHeaders.SET_COOKIE), contains("refreshToken=refreshToken"));
    }

    @Test
    @DisplayName("로그인 성공 - 온보딩 완료 회원은 hasOnboarding true 반환")
    void login_withOnboarding() {
        LoginRequest request = LoginRequestFixture.defaultRequest();
        given(authService.login(request)).willReturn(new LoginResult("accessToken", "refreshToken", true));

        ResponseEntity<BaseResponse<LoginResponse>> response = authController.login(request, httpServletResponse);

        assert response.getBody() != null;
        assertThat(response.getBody().data().hasOnboarding()).isTrue();
    }

    @Test
    @DisplayName("로그인 성공 - 온보딩 미완료 회원은 hasOnboarding false 반환")
    void login_withoutOnboarding() {
        LoginRequest request = LoginRequestFixture.defaultRequest();
        given(authService.login(request)).willReturn(new LoginResult("accessToken", "refreshToken", false));

        ResponseEntity<BaseResponse<LoginResponse>> response = authController.login(request, httpServletResponse);

        assert response.getBody() != null;
        assertThat(response.getBody().data().hasOnboarding()).isFalse();
    }

    @Test
    @DisplayName("토큰 재발급 성공 - 200 OK, 새 accessToken Body 반환")
    void refresh_success() {
        given(authService.refresh("oldRefreshToken")).willReturn(new RefreshResult("newAccessToken", "newRefreshToken"));

        ResponseEntity<BaseResponse<RefreshResponse>> response = authController.refresh("oldRefreshToken", httpServletResponse);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        assert response.getBody() != null;
        assertThat(response.getBody().data().accessToken()).isEqualTo("newAccessToken");
    }

    @Test
    @DisplayName("토큰 재발급 성공 - 새 refreshToken을 HttpOnly Cookie로 교체")
    void refresh_setsNewCookie() {
        given(authService.refresh("oldRefreshToken")).willReturn(new RefreshResult("newAccessToken", "newRefreshToken"));

        authController.refresh("oldRefreshToken", httpServletResponse);

        verify(httpServletResponse).addHeader(eq(HttpHeaders.SET_COOKIE), contains("refreshToken=newRefreshToken"));
    }

    @Test
    @DisplayName("쿠키 없이 재발급 요청 - ServiceErrorException 발생")
    void refresh_noToken() {
        assertThatThrownBy(() -> authController.refresh(null, httpServletResponse))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("잘못된 인증 정보입니다, 다시 로그인 하시기 바랍니다");
    }
}
