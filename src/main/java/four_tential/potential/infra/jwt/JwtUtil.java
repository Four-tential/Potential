package four_tential.potential.infra.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {
    // 만료 시간 가져오기
    @Value("${jwt.secret.accessExpire}")
    private Long accessTokenExpire;

    @Value("${jwt.secret.refreshExpire}")
    private Long refreshTokenExpire;

    // 비밀키 구문 가져오기
    @Value("${jwt.secret.key}")
    private String secretKeyStr;

    // 서명에 사용할 비밀키와 검증을 위한 Parser
    private SecretKey secretKey;
    private JwtParser parser;

    @PostConstruct
    public void init() {
        byte[] bytes = Decoders.BASE64.decode(secretKeyStr); // 비밀키의 Base64 변환
        this.secretKey = Keys.hmacShaKeyFor(bytes); // HMAC-SHA-256 알고리즘으로 서명에 필요한 비밀키 생성 (공식문서)
        this.parser = Jwts.parser().verifyWith(this.secretKey).build(); // 비밀키를 사용한 parser 선언
    }

    // 토큰 생성
    public String createAccessToken(String email, UUID memberId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("memberId", memberId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpire))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public String createRefreshToken(String email) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpire))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    // 토큰 검증
    public boolean validateToken(String token) {
        if(token == null || token.isBlank()) return false;

        try {
            parser.parseSignedClaims(token);
            return true;
        } catch (JwtException e) {
            log.error("JwtException : {}", e.getMessage());
            return false;
        }
    }

    // 서명은 유효하지만 만료된 토큰인지 확인
    public boolean isExpiredToken(String token) {
        if(token == null || token.isBlank()) return false;

        try {
            parser.parseSignedClaims(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    // 만료된 토큰에서도 Subject 추출 (로그아웃 등 만료 허용 케이스)
    public String extractSubjectAllowExpired(String token) {
        try {
            return parser.parseSignedClaims(token).getPayload().getSubject();
        } catch (ExpiredJwtException e) {
            return e.getClaims().getSubject();
        }
    }

    // 블랙리스트를 위한 accessToken 의 남은 만료시간 계산
    public long getRemainingTime(String accessToken) {
        Date expirationDate = parser.parseSignedClaims(accessToken).getPayload().getExpiration();
        return expirationDate.getTime() - new Date().getTime();
    }

    // Subject 추출하기
    public String extractSubject(String token) {
        return parser.parseSignedClaims(token).getPayload().getSubject();
    }

    // Claim 내 Role 추출하기
    public String extractRoleByToken(String token) {
        return parser.parseSignedClaims(token).getPayload().get("role", String.class);
    }

    // Claim 내 MemberId 추출하기
    public String extractMemberIdByToken(String token) {
        return parser.parseSignedClaims(token).getPayload().get("memberId", String.class);
    }

}
