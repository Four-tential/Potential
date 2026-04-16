package four_tential.potential.presentation.auth.model;

public record RefreshResult(
        String newAccessToken,
        String newRefreshToken
) {
}
