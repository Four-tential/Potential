package four_tential.potential.application.auth;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.MemberExceptionEnum;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.member.member.MemberStatus;
import four_tential.potential.infra.jwt.JwtUtil;
import four_tential.potential.infra.jwt.JwtRepository;
import four_tential.potential.presentation.auth.model.LoginResult;
import four_tential.potential.presentation.auth.model.RefreshResult;
import four_tential.potential.presentation.auth.model.request.LoginRequest;
import four_tential.potential.presentation.auth.model.request.SignUpRequest;
import four_tential.potential.presentation.auth.model.response.SignUpResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static four_tential.potential.common.exception.domain.MemberExceptionEnum.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    private final JwtRepository jwtRepository;
    private final MemberRepository memberRepository;

    @Value("${jwt.secret.refreshExpire}")
    private Long refreshTokenExpire;

    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {
        boolean memberExists = memberRepository.existsByEmail(request.email());

        if (memberExists) {
            throw new ServiceErrorException(MemberExceptionEnum.ERR_DUPLICATED_EMAIL);
        }

        Member newMember = Member.register(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name(),
                request.phone()
        );

        memberRepository.save(newMember);

        return new SignUpResponse(newMember.getEmail(), newMember.getName(), newMember.getRole().name(), newMember.getStatus().name());
    }

    @Transactional(readOnly = true)
    public LoginResult login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email()).orElseThrow(() -> new ServiceErrorException(ERR_WRONG_LOGIN));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new ServiceErrorException(ERR_WRONG_LOGIN);
        }

        if(member.getStatus() == MemberStatus.WITHDRAWAL) {
            throw new ServiceErrorException(ERR_WRONG_LOGIN);
        }

        if(member.getStatus() == MemberStatus.SUSPENDED) {
            throw new ServiceErrorException(ERR_SUSPENDED);
        }

        String role = member.getRole().name();

        String accessToken = jwtUtil.createAccessToken(request.email(), member.getId(), role);
        String refreshToken = jwtUtil.createRefreshToken(request.email());

        jwtRepository.saveRefreshToken(member.getEmail(), refreshToken, refreshTokenExpire);

        return new LoginResult(accessToken, refreshToken, member.isHasOnboarding());
    }

    @Transactional(readOnly = true)
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

    public void logOut(String accessToken) {
        if(!jwtUtil.validateToken(accessToken)) {
            throw new ServiceErrorException(ERR_INVALID_AUTHORIZE);
        }

        String email = jwtUtil.extractSubject(accessToken);
        jwtRepository.deleteRefreshToken(email);
        jwtRepository.addBlacklist(accessToken, jwtUtil.getRemainingTime(accessToken));
    }

}
