package four_tential.potential.infra.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private JwtRepository jwtRepository;
    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtFilter jwtFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String TEST_EMAIL = "test@test.com";
    private static final String TEST_ROLE = "ROLE_STUDENT";
    private static final UUID TEST_MEMBER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // JwtFilter는 ObjectMapper를 생성자 주입받는데, tools.jackson ObjectMapper는 new로 생성 가능
        jwtFilter = new JwtFilter(jwtUtil, new ObjectMapper(), jwtRepository);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("shouldNotFilter - 제외 규칙 테스트")
    class ShouldNotFilterTest {

        @ParameterizedTest(name = "{0} {1} → 필터 제외")
        @CsvSource({
                "GET, /swagger-ui/index.html",
                "GET, /swagger-ui.html",
                "GET, /v3/api-docs/swagger-config",
                "GET, /actuator/health",
                "POST, /v1/auth/signup",
                "POST, /v1/auth/login",
                "POST, /v1/auth/refresh",
                "GET, /v1/courses",
                "GET, /v1/courses/00000000-0000-0000-0000-000000000001",
                "GET, /v1/courses/00000000-0000-0000-0000-000000000001/reviews",
                "GET, /v1/instructors/00000000-0000-0000-0000-000000000001",
                "POST, /v1/webhooks/portone",
                "GET, /v1/payments/portone-config",
                "GET, /payment-test.html",
        })
        @DisplayName("허용된 메서드+경로 조합은 필터를 건너뛴다")
        void excludedPaths(String method, String uri) {
            request.setMethod(method);
            request.setRequestURI(uri);

            assertThat(jwtFilter.shouldNotFilter(request)).isTrue();
        }

        @ParameterizedTest(name = "{0} {1} → 필터 통과")
        @CsvSource({
                "POST, /v1/courses",
                "PATCH, /v1/courses/00000000-0000-0000-0000-000000000001",
                "DELETE, /v1/courses/00000000-0000-0000-0000-000000000001/wishlist-courses",
                "GET, /v1/auth/signup",
                "GET, /v1/members/me",
                "GET, /v1/instructors/me/courses",
                "GET, /v1/instructors/00000000-0000-0000-0000-000000000001/courses",
                "POST, /v1/instructors/00000000-0000-0000-0000-000000000001/follows",
        })
        @DisplayName("허용되지 않은 메서드+경로 조합은 필터를 통과한다")
        void nonExcludedPaths(String method, String uri) {
            request.setMethod(method);
            request.setRequestURI(uri);

            assertThat(jwtFilter.shouldNotFilter(request)).isFalse();
        }

        @ParameterizedTest(name = "OPTIONS {0} → 필터 제외")
        @CsvSource({
                "/v1/auth/login",
                "/v1/members/me",
                "/v1/courses/00000000-0000-0000-0000-000000000001/wishlist-courses",
        })
        @DisplayName("OPTIONS 프리플라이트 요청은 경로와 무관하게 필터를 건너뛴다")
        void optionsPreflight(String uri) {
            request.setMethod("OPTIONS");
            request.setRequestURI(uri);

            assertThat(jwtFilter.shouldNotFilter(request)).isTrue();
        }
    }

    @Nested
    @DisplayName("doFilterInternal - 필터 동작 테스트")
    class DoFilterInternalTest {

        @Test
        @DisplayName("Authorization 헤더가 없으면 401을 반환한다")
        void noAuthorizationHeader() throws ServletException, IOException {
            request.setMethod("GET");
            request.setRequestURI("/v1/members/me");

            jwtFilter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("인증 정보가 없습니다");
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("Bearer 접두사가 없으면 401을 반환한다")
        void noBearerPrefix() throws ServletException, IOException {
            request.setMethod("GET");
            request.setRequestURI("/v1/members/me");
            request.addHeader("Authorization", "Basic sometoken");

            jwtFilter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("인증 정보가 없습니다");
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("유효한 토큰이면 SecurityContext에 인증 정보를 설정하고 필터 체인을 진행한다")
        void validToken() throws ServletException, IOException {
            request.setMethod("GET");
            request.setRequestURI("/v1/members/me");
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);

            given(jwtUtil.validateToken(VALID_TOKEN)).willReturn(true);
            given(jwtRepository.isBlacklist(VALID_TOKEN)).willReturn(false);
            given(jwtUtil.extractSubject(VALID_TOKEN)).willReturn(TEST_EMAIL);
            given(jwtUtil.extractRoleByToken(VALID_TOKEN)).willReturn(TEST_ROLE);
            given(jwtUtil.extractMemberIdByToken(VALID_TOKEN)).willReturn(TEST_MEMBER_ID.toString());

            jwtFilter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(200);
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getPrincipal()).isInstanceOf(four_tential.potential.infra.security.principal.MemberPrincipal.class);
            var principal = (four_tential.potential.infra.security.principal.MemberPrincipal) auth.getPrincipal();
            assertThat(principal.email()).isEqualTo(TEST_EMAIL);
            assertThat(principal.memberId()).isEqualTo(TEST_MEMBER_ID);
            assertThat(principal.role()).isEqualTo(TEST_ROLE);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("토큰 검증 실패 시 401을 반환한다")
        void invalidToken() throws ServletException, IOException {
            request.setMethod("GET");
            request.setRequestURI("/v1/members/me");
            request.addHeader("Authorization", "Bearer invalid-token");

            given(jwtUtil.validateToken("invalid-token")).willReturn(false);

            jwtFilter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("인증 정보가 유효하지 않습니다");
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("블랙리스트 토큰이면 401을 반환한다")
        void blacklistedToken() throws ServletException, IOException {
            request.setMethod("GET");
            request.setRequestURI("/v1/members/me");
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);

            given(jwtUtil.validateToken(VALID_TOKEN)).willReturn(true);
            given(jwtRepository.isBlacklist(VALID_TOKEN)).willReturn(true);

            jwtFilter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("인증 정보가 유효하지 않습니다");
            verify(filterChain, never()).doFilter(request, response);
        }
    }
}
