package four_tential.potential.infra.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final String EMAIL_CONFLICT_CODE = "ERR_SOCIAL_EMAIL_CONFLICT";

    @Value("${oauth2.failure-redirect-uri:http://localhost:3000/oauth/callback}")
    private String failureRedirectUri;

    @Value("${oauth2.link-confirm-redirect-uri:http://localhost:8080/oauth-link-confirm.html}")
    private String linkConfirmRedirectUri;

    @Override
    public void onAuthenticationFailure(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        String errorCode = "oauth2_login_failed";
        String description = exception.getMessage();

        OAuth2AuthenticationException oae = unwrapOAuth2(exception);
        if (oae != null) {
            errorCode = oae.getError().getErrorCode();
            description = oae.getError().getDescription() != null ? oae.getError().getDescription() : description;
        }

        if (EMAIL_CONFLICT_CODE.equals(errorCode)) {
            String redirectUrl = buildLinkConfirmRedirect(description);
            log.info("소셜 로그인 - 이메일 충돌 챌린지 페이지로 이동");
            getRedirectStrategy().sendRedirect(request, response, redirectUrl);
            return;
        }

        log.warn("소셜 로그인 실패 - code={}, message={}", errorCode, description);

        String redirectUrl = UriComponentsBuilder.fromUriString(failureRedirectUri)
                .queryParam("error", errorCode)
                .queryParam("message", description)
                .build()
                .encode()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private OAuth2AuthenticationException unwrapOAuth2(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof OAuth2AuthenticationException oae) {
                return oae;
            }
            current = current.getCause();
        }
        return null;
    }

    private String buildLinkConfirmRedirect(String description) {
        String challengeToken = null;
        String email = null;
        String provider = null;

        if (description != null) {
            for (String part : description.split("&")) {
                int idx = part.indexOf('=');
                if (idx <= 0) continue;
                String key = part.substring(0, idx);
                String value = part.substring(idx + 1);
                switch (key) {
                    case "challengeToken" -> challengeToken = value;
                    case "email" -> email = value;
                    case "provider" -> provider = value;
                    default -> { /* ignore */ }
                }
            }
        }

        return UriComponentsBuilder.fromUriString(linkConfirmRedirectUri)
                .queryParam("challengeToken", challengeToken)
                .queryParam("email", email)
                .queryParam("provider", provider)
                .build()
                .encode()
                .toUriString();
    }
}
