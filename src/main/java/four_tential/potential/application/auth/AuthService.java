package four_tential.potential.application.auth;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.MemberExceptionEnum;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.presentation.auth.model.request.SignUpRequest;
import four_tential.potential.presentation.auth.model.response.SignUpResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;

    @Value("${member.default-profile-image-url}")
    private String defaultProfileImageUrl;

    @Transactional
    public SignUpResponse saveMember(SignUpRequest request, String encodedPassword) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new ServiceErrorException(MemberExceptionEnum.ERR_DUPLICATED_EMAIL);
        }

        Member newMember = Member.register(
                request.email(),
                encodedPassword,
                request.name(),
                request.phone()
        );

        newMember.setProfileImageUrl(defaultProfileImageUrl);
        memberRepository.save(newMember);

        return new SignUpResponse(
                newMember.getEmail(),
                newMember.getName(),
                newMember.getRole().name(),
                newMember.getStatus().name()
        );
    }
}
