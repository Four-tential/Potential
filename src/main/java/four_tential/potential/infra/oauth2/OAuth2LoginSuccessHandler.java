package four_tential.potential.infra.oauth2;

import four_tential.potential.application.auth.OAuthLoginTicketData;
import four_tential.potential.application.auth.OAuthRedirectTicketRepository;
import four_tential.potential.infra.jwt.JwtRepository;
import four_tential.potential.infra.jwt.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final JwtRepository jwtRepository;
    private final OAuthRedirectTicketRepository ticketRepository;

    @Value("${jwt.secret.refreshExpire:604800000}")
    private Long refreshTokenExpire;

    @Value("${oauth2.success-redirect-uri}")
    private String successRedirectUri;

    @Value("${oauth2.cookie.secure:true}")
    private boolean cookieSecure;

    @Override
    public void onAuthenticationSuccess(
            @NonNull HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();

        assert principal != null;
        String accessToken = jwtUtil.createAccessToken(
                principal.getEmail(),
                principal.getMemberId(),
                principal.getRole().name()
        );
        String refreshToken = jwtUtil.createRefreshToken(principal.getEmail());

        // 티켓 발급이 성공한 뒤에 토큰/쿠키를 저장해야 부분 성공이 남지 않음
        String ticket = ticketRepository.issueLogin(new OAuthLoginTicketData(
                accessToken,
                principal.isHasOnboarding(),
                principal.isRequiresPhoneSetup()
        ));

        jwtRepository.saveRefreshToken(principal.getEmail(), refreshToken, refreshTokenExpire);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie(refreshToken).toString());

        String redirectUrl = UriComponentsBuilder.fromUriString(successRedirectUri)
                .queryParam("ticket", ticket)
                .build()
                .toUriString();

        log.info("소셜 로그인 성공 - memberId={}, redirect={}", principal.getMemberId(), successRedirectUri);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private ResponseCookie refreshTokenCookie(String refreshToken) {
        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/v1/auth")
                .maxAge(Duration.ofMillis(refreshTokenExpire))
                .build();
    }
}
