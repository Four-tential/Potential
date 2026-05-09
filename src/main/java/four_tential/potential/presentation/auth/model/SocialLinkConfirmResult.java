package four_tential.potential.presentation.auth.model;

import four_tential.potential.domain.member.social.SocialProvider;

public record SocialLinkConfirmResult(
        String accessToken,
        String refreshToken,
        boolean hasOnboarding,
        boolean requiresPhoneSetup,
        SocialProvider linkedProvider,
        String email
) {
}
