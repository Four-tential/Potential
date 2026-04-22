package four_tential.potential.infra.jwt;

import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final JwtRepository jwtRepository;

    private static final PathPatternParser patternParser = new PathPatternParser();

    // 통과 API 패턴 기록 (Path Pattern 사용)
    private static final List<PathPattern> EXCLUDE_PATTERNS = List.of(
            // Swagger
            patternParser.parse("/swagger-ui/**"),
            patternParser.parse("/swagger-ui.html"),
            patternParser.parse("/v3/api-docs/**"),

            // Auth
            patternParser.parse("/v1/auth/signup"),
            patternParser.parse("/v1/auth/login"),
            patternParser.parse("/v1/auth/refresh"),

            // Course
            patternParser.parse("/v1/courses"),

            // Actuator (Monitoring)
            patternParser.parse("/actuator/**"),

            // 결제 테스트 관련 추가
            patternParser.parse("/payment-test.html"),
            patternParser.parse("/v1/payments/portone-config"),
            patternParser.parse("/v1/webhooks/portone")
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        log.info("JwtAuthFilter IN");
        String Authorization = request.getHeader("Authorization");
        String token;

        // Header 의 Authorization 확인
        if (Authorization != null && Authorization.startsWith("Bearer ")) {
            token = Authorization.substring("Bearer ".length());
        } else {
            BaseResponse<Void> baseResponse = BaseResponse.fail(HttpStatus.UNAUTHORIZED.name(), "인증 정보가 없습니다");

            response.setContentType("application/json; charset=UTF-8");
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.getWriter().write(objectMapper.writeValueAsString(baseResponse));
            return;
        }

        if (jwtUtil.validateToken(token) && !jwtRepository.isBlacklist(token)) {
            String email = jwtUtil.extractSubject(token);
            String role = jwtUtil.extractRoleByToken(token);
            UUID memberId = UUID.fromString(jwtUtil.extractMemberIdByToken(token));

            MemberPrincipal principal = new MemberPrincipal(memberId, email, role);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            principal
                            , null
                            , List.of(new SimpleGrantedAuthority(role)))
            );
        } else {
            BaseResponse<Void> baseResponse = BaseResponse.fail(HttpStatus.UNAUTHORIZED.name(), "인증 정보가 유효하지 않습니다, 다시 로그인 후 시도하시기 바랍니다");

            response.setContentType("application/json; charset=UTF-8");
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.getWriter().write(objectMapper.writeValueAsString(baseResponse));
            return;
        }

        filterChain.doFilter(request, response);

        log.info("JwtAuthFilter OUT");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        PathContainer path = PathContainer.parsePath(request.getRequestURI());
        return EXCLUDE_PATTERNS.stream().anyMatch(pattern -> pattern.matches(path));
    }
}
