package four_tential.potential.presentation.auth.model;

public record LoginResult(
        String accessToken,
        String refreshToken,
        boolean hasOnboarding
) {
}
