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
import four_tential.potential.presentation.auth.model.LoginResult;
import four_tential.potential.presentation.auth.model.RefreshResult;
import four_tential.potential.presentation.auth.model.request.LoginRequest;
import four_tential.potential.presentation.auth.model.SocialLinkConfirmResult;
import four_tential.potential.presentation.auth.model.request.OAuthTicketExchangeRequest;
import four_tential.potential.presentation.auth.model.request.SignUpRequest;
import four_tential.potential.presentation.auth.model.request.SocialLinkConfirmRequest;
import four_tential.potential.presentation.auth.model.request.SocialLinkRequest;
import four_tential.potential.presentation.auth.model.response.LoginResponse;
import four_tential.potential.presentation.auth.model.response.OAuthLinkExchangeResponse;
import four_tential.potential.presentation.auth.model.response.OAuthLoginExchangeResponse;
import four_tential.potential.presentation.auth.model.response.RefreshResponse;
import four_tential.potential.presentation.auth.model.response.SignUpResponse;
import four_tential.potential.presentation.auth.model.response.SocialLinkConfirmResponse;
import four_tential.potential.presentation.auth.model.response.SocialLinkResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_OAUTH_TICKET_INVALID;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_TOKEN_NULL;

@Tag(name = "Auth - 소셜 로그인", description = "OAuth2 소셜 로그인 / 계정 연동 API")
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthFacade authFacade;
    private final OAuthRedirectTicketRepository oauthTicketRepository;

    @Value("${jwt.secret.refreshExpire}")
    private Long refreshTokenExpire;

    @Value("${oauth2.cookie.secure:true}")
    private boolean cookieSecure;

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
            description = "로그인한 회원이 마이페이지에서 비밀번호 인증 후 OAuth2 인가 코드로 소셜 계정을 연동합니다. " +
                    "Authorization Bearer accessToken 필요."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "연동 성공"),
            @ApiResponse(responseCode = "400", description = "ERR_WRONG_PASSWORD / ERR_NO_PASSWORD_SET / ERR_SOCIAL_PROVIDER_NOT_SUPPORTED / ERR_SOCIAL_PROFILE_INCOMPLETE",
                    content = @Content(examples = @ExampleObject(value = "{\"success\":false,\"status\":\"BAD_REQUEST\",\"message\":\"비밀번호가 올바르지 않습니다\"}"))),
            @ApiResponse(responseCode = "401", description = "ERR_INVALID_AUTHORIZE — 미인증/Authorization 헤더 누락"),
            @ApiResponse(responseCode = "409", description = "ERR_SOCIAL_LINK_DUPLICATED / ERR_SOCIAL_ALREADY_LINKED")
    })
    @PostMapping("/social-link/{provider}")
    public ResponseEntity<BaseResponse<SocialLinkResponse>> linkSocialAccount(
            @Parameter(description = "소셜 제공자 (kakao | google, 대소문자 무관)", example = "kakao")
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
            description = "소셜 로그인 시 동일 이메일의 기존 계정과 충돌하면 백엔드가 challengeToken 을 발급. " +
                    "프론트가 link-ticket/exchange 로 challengeToken/email/provider 를 받은 뒤, 이 API 로 비밀번호 검증 + 자동 연동 + 로그인.\n\n" +
                    "성공 시 Set-Cookie 로 refreshToken (HttpOnly, Secure, Path=/v1/auth) 발급."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "연동 + 로그인 성공"),
            @ApiResponse(responseCode = "400", description = "ERR_WRONG_PASSWORD / ERR_NO_PASSWORD_SET"),
            @ApiResponse(responseCode = "401", description = "ERR_INVALID_AUTHORIZE — challengeToken 만료/위조/이메일 누락"),
            @ApiResponse(responseCode = "403", description = "ERR_SUSPENDED — 정지된 회원"),
            @ApiResponse(responseCode = "404", description = "ERR_WRONG_LOGIN — 탈퇴 회원 또는 이메일 불일치")
    })
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

    @Operation(
            summary = "소셜 로그인 1회용 티켓 교환",
            description = "OAuth2 로그인 성공 후 redirect URL 쿼리(`?ticket=...`)로 받은 1회용 티켓을 accessToken/온보딩 정보로 교환합니다.\n\n" +
                    "- TTL: 60초 / Redis getAndDelete (1회만 유효)\n" +
                    "- refreshToken 은 Set-Cookie 로 이미 내려간 상태이므로 응답에 포함되지 않음\n" +
                    "- 이 엔드포인트는 인증 헤더 없이 호출 (permitAll)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "교환 성공",
                    content = @Content(examples = @ExampleObject(value = "{\"success\":true,\"status\":\"OK\",\"message\":\"소셜 로그인 티켓 교환 성공\",\"data\":{\"accessToken\":\"eyJ...\",\"hasOnboarding\":true,\"requiresPhoneSetup\":false}}"))),
            @ApiResponse(responseCode = "400", description = "ERR_OAUTH_TICKET_INVALID — 만료/이미 사용된 티켓",
                    content = @Content(examples = @ExampleObject(value = "{\"success\":false,\"status\":\"BAD_REQUEST\",\"message\":\"유효하지 않거나 만료된 티켓입니다\"}")))
    })
    @PostMapping("/oauth/ticket/exchange")
    public ResponseEntity<BaseResponse<OAuthLoginExchangeResponse>> exchangeLoginTicket(
            @Valid @RequestBody OAuthTicketExchangeRequest request
    ) {
        OAuthLoginTicketData data = oauthTicketRepository.consumeLogin(request.ticket())
                .orElseThrow(() -> new ServiceErrorException(ERR_OAUTH_TICKET_INVALID));

        return ResponseEntity.ok(BaseResponse.success(
                HttpStatus.OK.name(),
                "소셜 로그인 티켓 교환 성공",
                new OAuthLoginExchangeResponse(data.accessToken(), data.hasOnboarding(), data.requiresPhoneSetup())
        ));
    }

    @Operation(
            summary = "소셜 연동 챌린지 1회용 티켓 교환",
            description = "이메일 충돌 흐름에서 받은 linkTicket 을 challengeToken/email/provider 로 교환합니다.\n\n" +
                    "- 교환 후 프론트는 사용자에게 비밀번호 입력 화면 노출 → `/v1/auth/social-link/confirm` 호출\n" +
                    "- TTL 60초 / 1회용\n" +
                    "- 인증 헤더 불필요"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "교환 성공"),
            @ApiResponse(responseCode = "400", description = "ERR_OAUTH_TICKET_INVALID — 만료/이미 사용된 티켓")
    })
    @PostMapping("/oauth/link-ticket/exchange")
    public ResponseEntity<BaseResponse<OAuthLinkExchangeResponse>> exchangeLinkTicket(
            @Valid @RequestBody OAuthTicketExchangeRequest request
    ) {
        OAuthLinkTicketData data = oauthTicketRepository.consumeLink(request.ticket())
                .orElseThrow(() -> new ServiceErrorException(ERR_OAUTH_TICKET_INVALID));

        return ResponseEntity.ok(BaseResponse.success(
                HttpStatus.OK.name(),
                "소셜 연동 티켓 교환 성공",
                new OAuthLinkExchangeResponse(data.challengeToken(), data.email(), data.provider())
        ));
    }

    @Operation(summary = "소셜 계정 연동 해제", description = "마이페이지에서 특정 provider 의 연동을 해제합니다. Authorization Bearer accessToken 필요.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "해제 성공"),
            @ApiResponse(responseCode = "401", description = "ERR_INVALID_AUTHORIZE — 미인증"),
            @ApiResponse(responseCode = "404", description = "ERR_SOCIAL_LINK_NOT_FOUND — 해제할 연동 정보 없음")
    })
    @DeleteMapping("/social-link/{provider}")
    public ResponseEntity<BaseResponse<Void>> unlinkSocialAccount(
            @Parameter(description = "소셜 제공자 (kakao | google, 대소문자 무관)", example = "kakao")
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
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/v1/auth")
                .maxAge(Duration.ofMillis(refreshTokenExpire))
                .build();
    }

    private ResponseCookie expireRefreshTokenCookie() {
        return ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
    }

}
