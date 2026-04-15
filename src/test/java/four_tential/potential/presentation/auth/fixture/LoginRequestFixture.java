package four_tential.potential.presentation.auth.fixture;

import four_tential.potential.presentation.auth.model.request.LoginRequest;

public class LoginRequestFixture {

    public static final String DEFAULT_EMAIL = "test@example.com";
    public static final String DEFAULT_PASSWORD = "Password1234!@";

    public static LoginRequest defaultRequest() {
        return new LoginRequest(DEFAULT_EMAIL, DEFAULT_PASSWORD);
    }

    public static LoginRequest withEmail(String email) {
        return new LoginRequest(email, DEFAULT_PASSWORD);
    }

    public static LoginRequest withPassword(String password) {
        return new LoginRequest(DEFAULT_EMAIL, password);
    }
}
