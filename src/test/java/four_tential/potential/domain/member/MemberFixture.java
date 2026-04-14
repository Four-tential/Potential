package four_tential.potential.domain.member;

public class MemberFixture {

    public static final String DEFAULT_EMAIL = "test@example.com";
    public static final String DEFAULT_PASSWORD = "encodedPassword123!";
    public static final String DEFAULT_PHONE = "010-1234-5678";
    public static final String DEFAULT_NAME = "홍길동";
    public static final String DEFAULT_PROFILE_IMAGE_URL = "https://cdn.example.com/images/profile.jpg";

    public static Member defaultMember() {
        return Member.register(DEFAULT_EMAIL, DEFAULT_PASSWORD, DEFAULT_PHONE, DEFAULT_NAME);
    }
}
