package four_tential.potential.application.auth;

import four_tential.potential.domain.member.social.SocialProvider;

public record OAuthLinkTicketData(
        String challengeToken,
        String email,
        SocialProvider provider
) {
}
