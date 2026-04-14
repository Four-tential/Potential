package four_tential.potential.presentation.auth.model.response;

public record SignUpResponse(
        String email,
        String name,
        String role,
        String status
) {
}
