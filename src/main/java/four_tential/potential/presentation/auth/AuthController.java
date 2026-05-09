package four_tential.potential.presentation.auth;

import four_tential.potential.application.auth.AuthFacade;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.member.social.SocialProvider;
import four_tential.potential.infra.oauth2.OAuth2UserAttributes;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.auth.model.LoginResult;
import four_tential.potential.presentation.auth.model.RefreshResult;
import four_tential.potential.presentation.auth.model.request.LoginRequest;
import four_tential.potential.presentation.auth.model.SocialLinkConfirmResult;
import four_tential.potential.presentation.auth.model.request.SignUpRequest;
import four_tential.potential.presentation.auth.model.request.SocialLinkConfirmRequest;
import four_tential.potential.presentation.auth.model.request.SocialLinkRequest;
import four_tential.potential.presentation.auth.model.response.LoginResponse;
import four_tential.potential.presentation.auth.model.response.RefreshResponse;
import four_tential.potential.presentation.auth.model.response.SignUpResponse;
import four_tential.potential.presentation.auth.model.response.SocialLinkConfirmResponse;
import four_tential.potential.presentation.auth.model.response.SocialLinkResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_TOKEN_NULL;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthFacade authFacade;

    @Value("${jwt.secret.refreshExpire}")
    private Long refreshTokenExpire;

    @PostMapping("/signup")
    public ResponseEntity<BaseResponse<SignUpResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(HttpStatus.CREATED.name(), "회원 가입 성공", authFacade.signUp(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<BaseResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        LoginResult result = authFacade.login(request);

        // Refresh Token 은 쿠키에 담기
        response.addHeader(HttpHeaders.SET_COOKIE, createRefreshTokenCookie(result.refreshToken()).toString());

        return ResponseEntity.status(HttpStatus.OK).body(BaseResponse.success(HttpStatus.OK.name(), "로그인 성공", new LoginResponse(result.accessToken(), result.hasOnboarding())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<BaseResponse<RefreshResponse>> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        if (refreshToken == null) {
            throw new ServiceErrorException(ERR_TOKEN_NULL);
        }

        RefreshResult result = authFacade.refresh(refreshToken);

        // Refresh Token 은 쿠키에 담기
        response.addHeader(HttpHeaders.SET_COOKIE, createRefreshTokenCookie(result.newRefreshToken()).toString());

        return ResponseEntity.status(HttpStatus.OK).body(BaseResponse.success(HttpStatus.OK.name(), "토큰 재발급 성공", new RefreshResponse(result.newAccessToken())));
    }

    @PostMapping("/logout")
    public ResponseEntity<BaseResponse<Void>> logOut(
            @RequestHeader("Authorization") String authorization,
            HttpServletResponse response
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ServiceErrorException(ERR_TOKEN_NULL);
        }

        String accessToken = authorization.substring("Bearer ".length());
        authFacade.logOut(accessToken);

        // refreshToken 쿠키 만료
        response.addHeader(HttpHeaders.SET_COOKIE, expireRefreshTokenCookie().toString());

        return ResponseEntity.status(HttpStatus.OK).body(BaseResponse.success(HttpStatus.OK.name(), "로그아웃 성공", null));
    }

    @Operation(
            summary = "소셜 계정 수동 연동",
            description = "현재 로그인한 회원의 비밀번호 검증 후, 프론트가 받아온 OAuth2 인가 코드를 교환해 소셜 계정을 연동합니다."
    )
    @PostMapping("/social-link/{provider}")
    public ResponseEntity<BaseResponse<SocialLinkResponse>> linkSocialAccount(
            @PathVariable("provider") SocialProvider provider,
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody SocialLinkRequest request
    ) {
        OAuth2UserAttributes attributes = authFacade.linkSocialAccount(
                principal.memberId(), provider, request.password(), request.code(), request.redirectUri()
        );
        return ResponseEntity.status(HttpStatus.OK).body(BaseResponse.success(
                HttpStatus.OK.name(),
                "소셜 계정 연동 완료",
                new SocialLinkResponse(attributes.provider(), attributes.email())
        ));
    }

    @Operation(
            summary = "소셜 계정 연동 챌린지 확인 (이메일 충돌 흐름)",
            description = "소셜 로그인 시 동일 이메일의 기존 계정과 충돌한 경우, 비밀번호 검증 후 자동 연동 + 로그인."
    )
    @PostMapping("/social-link/confirm")
    public ResponseEntity<BaseResponse<SocialLinkConfirmResponse>> confirmSocialLink(
            @Valid @RequestBody SocialLinkConfirmRequest request,
            HttpServletResponse response
    ) {
        SocialLinkConfirmResult result = authFacade.confirmSocialLink(request.challengeToken(), request.password());
        response.addHeader(HttpHeaders.SET_COOKIE, createRefreshTokenCookie(result.refreshToken()).toString());

        return ResponseEntity.status(HttpStatus.OK).body(BaseResponse.success(
                HttpStatus.OK.name(),
                "소셜 계정 연동 + 로그인 완료",
                new SocialLinkConfirmResponse(
                        result.accessToken(),
                        result.hasOnboarding(),
                        result.requiresPhoneSetup(),
                        result.linkedProvider(),
                        result.email()
                )
        ));
    }

    @Operation(summary = "소셜 계정 연동 해제", description = "마이페이지에서 특정 provider 의 연동을 해제합니다.")
    @DeleteMapping("/social-link/{provider}")
    public ResponseEntity<BaseResponse<Void>> unlinkSocialAccount(
            @PathVariable("provider") SocialProvider provider,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        authFacade.unlinkSocialAccount(principal.memberId(), provider);
        return ResponseEntity.status(HttpStatus.OK).body(BaseResponse.success(
                HttpStatus.OK.name(), "소셜 계정 연동 해제 완료", null
        ));
    }

    private ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                //.secure(true) // 우선 개발 환경에 맞추어 https 전송은 주석처리
                .sameSite("Strict")
                .path("/v1/auth") // RTR/logout 에서만
                .maxAge(Duration.ofMillis(refreshTokenExpire))
                .build();
    }

    private ResponseCookie expireRefreshTokenCookie() {
        return ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .sameSite("Strict")
                .path("/v1/auth")
                .maxAge(Duration.ZERO) // 즉시 만료
                .build();
    }

}
