package four_tential.potential.application.auth;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.MemberExceptionEnum;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.presentation.auth.model.request.SignUpRequest;
import four_tential.potential.presentation.auth.model.response.SignUpResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final PasswordEncoder passwordEncoder;
    private final MemberRepository memberRepository;

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
}
