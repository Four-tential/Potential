package four_tential.potential.infra.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    private static final String TEST_EMAIL = "test@test.com";
    private static final UUID TEST_MEMBER_ID = UUID.randomUUID();
    private static final String TEST_ROLE = "ROLE_STUDENT";
    // HS256 최소 요구: 256bit(32byte) 이상
    private static final String TEST_SECRET = Base64.getEncoder().encodeToString("test-secret-key-for-jwt-testings".getBytes());

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKeyStr", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpire", 3_600_000L);    // 1시간
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpire", 1_209_600_000L); // 14일
        jwtUtil.init();
    }

    // 테스트용 만료된 토큰 생성 헬퍼
    private String buildExpiredToken() {
        byte[] bytes = Decoders.BASE64.decode(TEST_SECRET);
        SecretKey key = Keys.hmacShaKeyFor(bytes);
        Date past = new Date(System.currentTimeMillis() - 60_000); // 1분 전 만료
        return Jwts.builder()
                .subject(JwtUtilTest.TEST_EMAIL)
                .issuedAt(past)
                .expiration(past)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    // region createAccessToken
    @Test
    @DisplayName("Access Token 생성 - 유효한 JWT 문자열 반환")
    void createAccessToken() {
        String token = jwtUtil.createAccessToken(TEST_EMAIL, TEST_MEMBER_ID, TEST_ROLE);

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.validateToken(token)).isTrue();
    }
    // endregion

    // region createRefreshToken
    @Test
    @DisplayName("Refresh Token 생성 - 유효한 JWT 문자열 반환")
    void createRefreshToken() {
        String token = jwtUtil.createRefreshToken(TEST_EMAIL);

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.validateToken(token)).isTrue();
    }
    // endregion

    // region validateToken
    @Test
    @DisplayName("validateToken - 유효한 토큰은 true 반환")
    void validateToken_valid() {
        String token = jwtUtil.createAccessToken(TEST_EMAIL, TEST_MEMBER_ID, TEST_ROLE);

        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateToken - null은 false 반환")
    void validateToken_null() {
        assertThat(jwtUtil.validateToken(null)).isFalse();
    }

    @Test
    @DisplayName("validateToken - 빈 문자열은 false 반환")
    void validateToken_blank() {
        assertThat(jwtUtil.validateToken("")).isFalse();
    }

    @Test
    @DisplayName("validateToken - 만료된 토큰은 false 반환")
    void validateToken_expired() {
        String expiredToken = buildExpiredToken();

        assertThat(jwtUtil.validateToken(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("validateToken - 위변조된 토큰은 false 반환")
    void validateToken_tampered() {
        String token = jwtUtil.createAccessToken(TEST_EMAIL, TEST_MEMBER_ID, TEST_ROLE);

        assertThat(jwtUtil.validateToken(token + "tampered")).isFalse();
    }

    @Test
    @DisplayName("isExpiredToken - 만료된 토큰은 true 반환")
    void isExpiredToken_expired() {
        String expiredToken = buildExpiredToken();

        assertThat(jwtUtil.isExpiredToken(expiredToken)).isTrue();
    }

    @Test
    @DisplayName("isExpiredToken - 유효한 토큰은 false 반환")
    void isExpiredToken_valid() {
        String token = jwtUtil.createAccessToken(TEST_EMAIL, TEST_MEMBER_ID, TEST_ROLE);

        assertThat(jwtUtil.isExpiredToken(token)).isFalse();
    }

    @Test
    @DisplayName("isExpiredToken - 위변조된 토큰은 false 반환 (만료 아님)")
    void isExpiredToken_tampered() {
        String token = jwtUtil.createAccessToken(TEST_EMAIL, TEST_MEMBER_ID, TEST_ROLE);

        assertThat(jwtUtil.isExpiredToken(token + "tampered")).isFalse();
    }

    @Test
    @DisplayName("extractSubjectAllowExpired - 유효한 토큰에서 email 추출")
    void extractSubjectAllowExpired_valid() {
        String token = jwtUtil.createAccessToken(TEST_EMAIL, TEST_MEMBER_ID, TEST_ROLE);

        assertThat(jwtUtil.extractSubjectAllowExpired(token)).isEqualTo(TEST_EMAIL);
    }

    @Test
    @DisplayName("extractSubjectAllowExpired - 만료된 토큰에서도 email 추출")
    void extractSubjectAllowExpired_expired() {
        String expiredToken = buildExpiredToken();

        assertThat(jwtUtil.extractSubjectAllowExpired(expiredToken)).isEqualTo(TEST_EMAIL);
    }

    @Test
    @DisplayName("extractSubject - 유효한 토큰에서 email 추출")
    void extractSubject() {
        String token = jwtUtil.createAccessToken(TEST_EMAIL, TEST_MEMBER_ID, TEST_ROLE);

        assertThat(jwtUtil.extractSubject(token)).isEqualTo(TEST_EMAIL);
    }

    @Test
    @DisplayName("extractRoleByToken - Access Token에서 role 추출")
    void extractRoleByToken() {
        String token = jwtUtil.createAccessToken(TEST_EMAIL, TEST_MEMBER_ID, TEST_ROLE);

        assertThat(jwtUtil.extractRoleByToken(token)).isEqualTo(TEST_ROLE);
    }

    @Test
    @DisplayName("extractMemberIdByToken - Access Token에서 memberId 추출")
    void extractMemberIdByToken() {
        String token = jwtUtil.createAccessToken(TEST_EMAIL, TEST_MEMBER_ID, TEST_ROLE);

        assertThat(jwtUtil.extractMemberIdByToken(token)).isEqualTo(TEST_MEMBER_ID.toString());
    }

    @Test
    @DisplayName("getRemainingTime - 유효한 토큰의 남은 만료 시간은 양수")
    void getRemainingTime_positive() {
        String token = jwtUtil.createAccessToken(TEST_EMAIL, TEST_MEMBER_ID, TEST_ROLE);

        assertThat(jwtUtil.getRemainingTime(token)).isPositive();
    }

    @Test
    @DisplayName("getRemainingTime - 만료 시간은 설정값(1시간) 이하")
    void getRemainingTime_withinExpire() {
        String token = jwtUtil.createAccessToken(TEST_EMAIL, TEST_MEMBER_ID, TEST_ROLE);

        assertThat(jwtUtil.getRemainingTime(token)).isLessThanOrEqualTo(3_600_000L);
    }
}
