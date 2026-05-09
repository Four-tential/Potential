package four_tential.potential.presentation.auth.model.response;

public record OAuthLoginExchangeResponse(
        String accessToken,
        boolean hasOnboarding,
        boolean requiresPhoneSetup
) {
}
