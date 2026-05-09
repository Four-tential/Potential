package four_tential.potential.presentation.auth.model.response;

import four_tential.potential.domain.member.social.SocialProvider;

public record SocialLinkConfirmResponse(
        String accessToken,
        boolean hasOnboarding,
        boolean requiresPhoneSetup,
        SocialProvider linkedProvider,
        String email
) {
}
