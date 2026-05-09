package four_tential.potential.infra.oauth2;

import four_tential.potential.application.auth.SocialAuthService;
import four_tential.potential.application.auth.SocialEmailConflictException;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.member.member.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final SocialAuthService socialAuthService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserAttributes attributes = OAuth2UserAttributes.from(registrationId, oAuth2User.getAttributes());

        try {
            Member member = socialAuthService.loginOrSignup(attributes);
            return new CustomOAuth2User(
                    member.getId(),
                    member.getEmail(),
                    member.getRole(),
                    member.isHasOnboarding(),
                    member.requiresPhoneSetup(),
                    oAuth2User.getAttributes()
            );
        } catch (SocialEmailConflictException e) {
            String code = e.getErrorCode() instanceof Enum<?> en ? en.name() : "social_email_conflict";
            String description = "challengeToken=" + e.getChallengeToken()
                    + "&email=" + e.getConflictEmail()
                    + "&provider=" + e.getProvider().name();
            log.info("소셜 로그인 - 이메일 충돌, 챌린지 발급: provider={}, email={}", e.getProvider(), e.getConflictEmail());
            OAuth2AuthenticationException oauth2Ex = new OAuth2AuthenticationException(
                    new OAuth2Error(code, description, null),
                    e
            );
            throw new InternalAuthenticationServiceException(description, oauth2Ex);
        } catch (ServiceErrorException e) {
            String code = e.getErrorCode() instanceof Enum<?> en ? en.name() : "social_error";
            log.warn("소셜 로그인 처리 실패 - code={}, message={}", code, e.getMessage());
            OAuth2AuthenticationException oauth2Ex = new OAuth2AuthenticationException(
                    new OAuth2Error(code, e.getMessage(), null),
                    e
            );
            throw new InternalAuthenticationServiceException(e.getMessage(), oauth2Ex);
        }
    }
}
