package four_tential.potential.application.auth;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.member.social.MemberSocialAccount;
import four_tential.potential.domain.member.social.MemberSocialAccountRepository;
import four_tential.potential.domain.member.social.SocialProvider;
import four_tential.potential.infra.oauth2.OAuth2UserAttributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_SOCIAL_ALREADY_LINKED;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_SOCIAL_LINK_DUPLICATED;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_SOCIAL_LINK_NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialAuthService {

    private final MemberRepository memberRepository;
    private final MemberSocialAccountRepository socialAccountRepository;
    private final SocialLinkChallengeRepository linkChallengeRepository;

    @Value("${member.default-profile-image-url}")
    private String defaultProfileImageUrl;

    @Transactional
    public Member loginOrSignup(OAuth2UserAttributes attributes) {
        SocialProvider provider = attributes.provider();
        String providerId = attributes.providerId();
        String email = attributes.email();
        String name = attributes.name();

        return socialAccountRepository.findByProviderAndProviderId(provider, providerId)
                .map(account -> memberRepository.findById(account.getMemberId())
                        .orElseThrow(() -> new IllegalStateException("연동 데이터에 매칭되는 회원이 없습니다 - memberId=" + account.getMemberId())))
                .orElseGet(() -> registerNewSocialMember(provider, providerId, email, name));
    }

    private Member registerNewSocialMember(SocialProvider provider, String providerId, String email, String name) {
        if (email != null && memberRepository.existsByEmail(email)) {
            log.info("소셜 로그인 - 이메일 충돌: provider={}, email={}", provider, email);
            String challengeToken = linkChallengeRepository.issue(
                    SocialLinkChallengeData.from(new OAuth2UserAttributes(provider, providerId, email, name))
            );
            throw new SocialEmailConflictException(challengeToken, email, provider);
        }

        Member newMember = Member.registerSocial(email, name);
        newMember.setProfileImageUrl(defaultProfileImageUrl);
        memberRepository.save(newMember);

        socialAccountRepository.save(MemberSocialAccount.link(newMember.getId(), provider, providerId, email));
        log.info("소셜 회원 신규 가입: provider={}, memberId={}", provider, newMember.getId());
        return newMember;
    }

    @Transactional
    public void linkExistingMember(UUID memberId, OAuth2UserAttributes attributes) {
        SocialProvider provider = attributes.provider();
        String providerId = attributes.providerId();

        socialAccountRepository.findByProviderAndProviderId(provider, providerId).ifPresent(existing -> {
            if (!existing.getMemberId().equals(memberId)) {
                throw new ServiceErrorException(ERR_SOCIAL_LINK_DUPLICATED);
            }
            throw new ServiceErrorException(ERR_SOCIAL_ALREADY_LINKED);
        });

        if (socialAccountRepository.existsByMemberIdAndProvider(memberId, provider)) {
            throw new ServiceErrorException(ERR_SOCIAL_ALREADY_LINKED);
        }

        socialAccountRepository.save(MemberSocialAccount.link(memberId, provider, providerId, attributes.email()));
        log.info("소셜 계정 수동 연동: memberId={}, provider={}", memberId, provider);
    }

    @Transactional
    public void unlinkSocialAccount(UUID memberId, SocialProvider provider) {
        if (!socialAccountRepository.existsByMemberIdAndProvider(memberId, provider)) {
            throw new ServiceErrorException(ERR_SOCIAL_LINK_NOT_FOUND);
        }
        socialAccountRepository.deleteByMemberIdAndProvider(memberId, provider);
        log.info("소셜 계정 연동 해제: memberId={}, provider={}", memberId, provider);
    }
}
