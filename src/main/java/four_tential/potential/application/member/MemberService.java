package four_tential.potential.application.member;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.presentation.member.model.request.UpdateMyPageRequest;
import four_tential.potential.presentation.member.model.response.MyPageResponse;
import four_tential.potential.presentation.member.model.response.UpdateMyPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_NOT_FOUND_MEMBER;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_NO_UPDATE_FIELD;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    @Value("${member.default-profile-image-url}")
    private String defaultProfileImageUrl;

    @Transactional(readOnly = true)
    public MyPageResponse getMyPageInfo(UUID memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_MEMBER));

        return MyPageResponse.register(member, getProfileImageUrlOrDefault(member));
    }

    @Transactional
    public UpdateMyPageResponse updateMyPageInfo(UUID memberId, UpdateMyPageRequest request) {
        if (request.phone() == null && request.profileImageUrl() == null) {
            throw new ServiceErrorException(ERR_NO_UPDATE_FIELD);
        }

        Member member = memberRepository.findById(memberId).orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_MEMBER));

        member.updateInfo(request.phone(), request.profileImageUrl());

        return UpdateMyPageResponse.register(member, getProfileImageUrlOrDefault(member));
    }

    private String getProfileImageUrlOrDefault(Member member) {
        return member.getProfileImageUrl() != null ? member.getProfileImageUrl() : defaultProfileImageUrl;
    }
}
