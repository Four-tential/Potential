package four_tential.potential.presentation.auth.model.response;

public record LoginResponse(
        String accessToken,
        boolean hasOnboarding
) {
}
