package four_tential.potential.presentation.auth;

import four_tential.potential.application.auth.AuthService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.presentation.auth.model.LoginResult;
import four_tential.potential.presentation.auth.model.RefreshResult;
import four_tential.potential.presentation.auth.model.request.LoginRequest;
import four_tential.potential.presentation.auth.model.request.SignUpRequest;
import four_tential.potential.presentation.auth.model.response.LoginResponse;
import four_tential.potential.presentation.auth.model.response.RefreshResponse;
import four_tential.potential.presentation.auth.model.response.SignUpResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_REFRESH_TOKEN_NULL;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @Value("${jwt.secret.refreshExpire}")
    private Long refreshTokenExpire;

    @PostMapping("/signup")
    public ResponseEntity<BaseResponse<SignUpResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(HttpStatus.CREATED.name(), "회원 가입 성공", authService.signUp(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<BaseResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        LoginResult result = authService.login(request);

        // Refresh Token 은 쿠키에 담기
        ResponseCookie responseCookie = ResponseCookie.from("refreshToken", result.refreshToken())
                .httpOnly(true)
                //.secure(true) // 우선 개발 환경에 맞추어 https 전송은 주석처리
                .sameSite("Strict")
                .path("/v1/auth") // RTR/logout 에서만
                .maxAge(Duration.ofMillis(refreshTokenExpire))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());

        return ResponseEntity.status(HttpStatus.OK).body(BaseResponse.success(HttpStatus.OK.name(), "로그인 성공", new LoginResponse(result.accessToken(), result.hasOnboarding())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<BaseResponse<RefreshResponse>> login(
            @CookieValue("refreshToken") String refreshToken,
            HttpServletResponse response
    ) {
        if (refreshToken == null) {
            throw new ServiceErrorException(ERR_REFRESH_TOKEN_NULL);
        }

        RefreshResult result = authService.refresh(refreshToken);

        // Refresh Token 은 쿠키에 담기
        ResponseCookie responseCookie = ResponseCookie.from("refreshToken", result.newRefreshToken())
                .httpOnly(true)
                //.secure(true) // 우선 개발 환경에 맞추어 https 전송은 주석처리
                .sameSite("Strict")
                .path("/v1/auth") // RTR/logout 에서만
                .maxAge(Duration.ofMillis(refreshTokenExpire))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());

        return ResponseEntity.status(HttpStatus.OK).body(BaseResponse.success(HttpStatus.OK.name(), "토큰 재발급 성공", new RefreshResponse(result.newAccessToken())));
    }

}
