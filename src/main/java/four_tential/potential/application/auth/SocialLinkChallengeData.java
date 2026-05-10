package four_tential.potential.application.auth;

import four_tential.potential.domain.member.social.SocialProvider;
import four_tential.potential.infra.oauth2.OAuth2UserAttributes;

public record SocialLinkChallengeData(
        SocialProvider provider,
        String providerId,
        String email,
        String name
) {
    public static SocialLinkChallengeData from(OAuth2UserAttributes attributes) {
        return new SocialLinkChallengeData(
                attributes.provider(),
                attributes.providerId(),
                attributes.email(),
                attributes.name()
        );
    }

    public OAuth2UserAttributes toAttributes() {
        return new OAuth2UserAttributes(provider, providerId, email, name);
    }
}
