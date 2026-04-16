package four_tential.potential.presentation.auth.fixture;

import four_tential.potential.presentation.auth.model.request.SignUpRequest;

public class SignUpRequestFixture {

    public static final String DEFAULT_EMAIL = "test@example.com";
    public static final String DEFAULT_PASSWORD = "Password1234!@";
    public static final String DEFAULT_NAME = "홍길동";
    public static final String DEFAULT_PHONE = "010-1234-5678";
    public static final String DEFAULT_PROFILE_IMAGE_URL = "https://bucketurl/default-profile-image.png";

    public static SignUpRequest defaultRequest() {
        return new SignUpRequest(DEFAULT_EMAIL, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_PHONE);
    }

    public static SignUpRequest withEmail(String email) {
        return new SignUpRequest(email, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_PHONE);
    }

    public static SignUpRequest withPassword(String password) {
        return new SignUpRequest(DEFAULT_EMAIL, password, DEFAULT_NAME, DEFAULT_PHONE);
    }

    public static SignUpRequest withPhone(String phone) {
        return new SignUpRequest(DEFAULT_EMAIL, DEFAULT_PASSWORD, DEFAULT_NAME, phone);
    }
}
