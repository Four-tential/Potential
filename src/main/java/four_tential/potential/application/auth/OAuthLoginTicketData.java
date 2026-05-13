package four_tential.potential.application.auth;

public record OAuthLoginTicketData(
        String accessToken,
        boolean hasOnboarding,
        boolean requiresPhoneSetup
) {
}
