package four_tential.potential.application.auth;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.MemberExceptionEnum;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.member.member.MemberStatus;
import four_tential.potential.domain.member.social.SocialProvider;
import four_tential.potential.infra.jwt.JwtUtil;
import four_tential.potential.infra.jwt.JwtRepository;
import four_tential.potential.infra.oauth2.OAuth2TokenExchangeClient;
import four_tential.potential.infra.oauth2.OAuth2UserAttributes;
import four_tential.potential.presentation.auth.model.LoginResult;
import four_tential.potential.presentation.auth.model.RefreshResult;
import four_tential.potential.presentation.auth.model.SocialLinkConfirmResult;
import four_tential.potential.presentation.auth.model.request.LoginRequest;
import four_tential.potential.presentation.auth.model.request.SignUpRequest;
import four_tential.potential.presentation.auth.model.response.SignUpResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import static four_tential.potential.common.exception.domain.MemberExceptionEnum.*;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_NO_PASSWORD_SET;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_WRONG_PASSWORD;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthFacade {
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    private final JwtRepository jwtRepository;
    private final MemberRepository memberRepository;
    private final AuthService authService;
    private final SocialAuthService socialAuthService;
    private final OAuth2TokenExchangeClient oAuth2TokenExchangeClient;
    private final SocialLinkChallengeRepository socialLinkChallengeRepository;

    @Value("${jwt.secret.refreshExpire}")
    private Long refreshTokenExpire;

    public SignUpResponse signUp(SignUpRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new ServiceErrorException(MemberExceptionEnum.ERR_DUPLICATED_EMAIL);
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        return authService.saveMember(request, encodedPassword);
    }

    public LoginResult login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new ServiceErrorException(ERR_WRONG_LOGIN));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new ServiceErrorException(ERR_WRONG_LOGIN);
        }

        if (member.getStatus() == MemberStatus.WITHDRAWAL) {
            throw new ServiceErrorException(ERR_WRONG_LOGIN);
        }

        if (member.getStatus() == MemberStatus.SUSPENDED) {
            throw new ServiceErrorException(ERR_SUSPENDED);
        }

        String role = member.getRole().name();

        String accessToken = jwtUtil.createAccessToken(member.getEmail(), member.getId(), role);
        String refreshToken = jwtUtil.createRefreshToken(member.getEmail());

        jwtRepository.saveRefreshToken(member.getEmail(), refreshToken, refreshTokenExpire);

        return new LoginResult(accessToken, refreshToken, member.isHasOnboarding());
    }

    public RefreshResult refresh(String refreshToken) {
        if(!jwtUtil.validateToken(refreshToken)) {
            log.error("Refresh Token Refresh ERR : {}", "토큰 유효성 검사 실패");
            throw new ServiceErrorException(ERR_INVALID_AUTHORIZE);
        }

        String email = jwtUtil.extractSubject(refreshToken);
        String savedToken = jwtRepository.getAndDeleteRefreshToken(email); // 조회와 삭제를 원자적으로 처리

        if(savedToken == null || !savedToken.equals(refreshToken)) {
            log.error("Refresh Token Refresh ERR : {}", "보유한 토큰과 불일치 또는 이미 사용된 토큰");
            throw new ServiceErrorException(ERR_INVALID_AUTHORIZE);
        }

        Member member = memberRepository.findByEmail(email).orElseThrow(() -> {
                log.error("Refresh Token Refresh ERR : {}", "토큰 내 계정 정보 이상");
                return new ServiceErrorException(ERR_INVALID_AUTHORIZE);
            }
        );

        if(member.getStatus() != MemberStatus.ACTIVE) {
            log.error("Refresh Token Refresh ERR : {}", "토큰 내 계정 정보 이상, 정상 계정 상태가 아님");
            throw new ServiceErrorException(ERR_INVALID_AUTHORIZE);
        }

        String newAccessToken = jwtUtil.createAccessToken(email, member.getId(), member.getRole().name());
        String newRefreshToken = jwtUtil.createRefreshToken(email);
        jwtRepository.saveRefreshToken(email, newRefreshToken, refreshTokenExpire);

        return new RefreshResult(newAccessToken, newRefreshToken);
    }

    public OAuth2UserAttributes linkSocialAccount(java.util.UUID memberId, SocialProvider provider, String password, String code, String redirectUri) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ServiceErrorException(ERR_INVALID_AUTHORIZE));

        if (member.getPassword() == null || member.getPassword().isBlank()) {
            throw new ServiceErrorException(ERR_NO_PASSWORD_SET);
        }

        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new ServiceErrorException(ERR_WRONG_PASSWORD);
        }

        OAuth2UserAttributes attributes = oAuth2TokenExchangeClient.exchangeAndFetch(
                provider.name().toLowerCase(), code, redirectUri
        );

        if (attributes.provider() != provider) {
            throw new ServiceErrorException(MemberExceptionEnum.ERR_SOCIAL_PROVIDER_NOT_SUPPORTED);
        }

        socialAuthService.linkExistingMember(memberId, attributes);
        return attributes;
    }

    public void unlinkSocialAccount(java.util.UUID memberId, SocialProvider provider) {
        socialAuthService.unlinkSocialAccount(memberId, provider);
    }

    public SocialLinkConfirmResult confirmSocialLink(String challengeToken, String password) {
        SocialLinkChallengeData data = socialLinkChallengeRepository.peek(challengeToken)
                .orElseThrow(() -> new ServiceErrorException(ERR_INVALID_AUTHORIZE));

        if (data.email() == null || data.email().isBlank()) {
            socialLinkChallengeRepository.invalidate(challengeToken);
            throw new ServiceErrorException(ERR_INVALID_AUTHORIZE);
        }

        Member member = memberRepository.findByEmail(data.email())
                .orElseThrow(() -> new ServiceErrorException(ERR_WRONG_LOGIN));

        if (member.getStatus() == MemberStatus.WITHDRAWAL) {
            throw new ServiceErrorException(ERR_WRONG_LOGIN);
        }
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            throw new ServiceErrorException(ERR_SUSPENDED);
        }

        if (member.getPassword() == null || member.getPassword().isBlank()) {
            throw new ServiceErrorException(ERR_NO_PASSWORD_SET);
        }

        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new ServiceErrorException(ERR_WRONG_PASSWORD);
        }

        socialLinkChallengeRepository.invalidate(challengeToken);
        socialAuthService.linkExistingMember(member.getId(), data.toAttributes());

        String role = member.getRole().name();
        String accessToken = jwtUtil.createAccessToken(member.getEmail(), member.getId(), role);
        String refreshToken = jwtUtil.createRefreshToken(member.getEmail());
        jwtRepository.saveRefreshToken(member.getEmail(), refreshToken, refreshTokenExpire);

        return new SocialLinkConfirmResult(
                accessToken,
                refreshToken,
                member.isHasOnboarding(),
                member.requiresPhoneSetup(),
                data.provider(),
                member.getEmail()
        );
    }

    public void logOut(String accessToken) {
        boolean isValid = jwtUtil.validateToken(accessToken);
        boolean isExpired = !isValid && jwtUtil.isExpiredToken(accessToken);

        if (!isValid && !isExpired) {
            // 서명 자체가 잘못된 토큰
            throw new ServiceErrorException(ERR_INVALID_AUTHORIZE);
        }

        String email = jwtUtil.extractSubjectAllowExpired(accessToken);
        jwtRepository.deleteRefreshToken(email);

        if (isValid) {
            // 아직 유효한 토큰만 블랙리스트 등록 (만료된 토큰은 이미 자연 소멸)
            jwtRepository.addBlacklist(accessToken, jwtUtil.getRemainingTime(accessToken));
        }
    }
}
