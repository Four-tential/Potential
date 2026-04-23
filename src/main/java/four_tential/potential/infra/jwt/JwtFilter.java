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

    private record ExcludeRule(String method, PathPattern pattern) {
        boolean matches(String reqMethod, PathContainer reqPath) {
            return (method == null || method.equalsIgnoreCase(reqMethod)) && pattern.matches(reqPath);
        }
    }

    private static ExcludeRule any(String path) {
        return new ExcludeRule(null, patternParser.parse(path));
    }

    private static ExcludeRule get(String path) {
        return new ExcludeRule("GET", patternParser.parse(path));
    }

    private static ExcludeRule post(String path) {
        return new ExcludeRule("POST", patternParser.parse(path));
    }

    private static final List<ExcludeRule> EXCLUDE_RULES = List.of(
            // Swagger, Actuator
            any("/swagger-ui/**"),
            any("/swagger-ui.html"),
            any("/v3/api-docs/**"),
            any("/actuator/**"),

            // Auth
            post("/v1/auth/signup"),
            post("/v1/auth/login"),
            post("/v1/auth/refresh"),

            // Course
            get("/v1/courses"),
            get("/v1/courses/{courseId}"),
            get("/v1/courses/{courseId}/reviews"),

            // Instructor profile
            get("/v1/instructors/{instructorId}"),

            // Payment
            post("/v1/webhooks/portone"),
            get("/v1/payments/portone-config"),

            // 결제 테스트 페이지
            any("/payment-test.html")
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        log.info("JwtAuthFilter IN");
        String authorization = request.getHeader("Authorization");
        String token;

        if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring("Bearer ".length());
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
        String method = request.getMethod();
        PathContainer path = PathContainer.parsePath(request.getRequestURI());
        return EXCLUDE_RULES.stream().anyMatch(rule -> rule.matches(method, path));
    }
}
