package four_tential.potential.application.auth;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.MemberExceptionEnum;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.member.member.MemberStatus;
import four_tential.potential.infra.jwt.JwtUtil;
import four_tential.potential.infra.jwt.JwtRepository;
import four_tential.potential.presentation.auth.model.LoginResult;
import four_tential.potential.presentation.auth.model.request.LoginRequest;
import four_tential.potential.presentation.auth.model.request.SignUpRequest;
import four_tential.potential.presentation.auth.model.response.SignUpResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_SUSPENDED;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_WRONG_LOGIN;

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

}
