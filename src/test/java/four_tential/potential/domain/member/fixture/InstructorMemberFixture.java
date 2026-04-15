package four_tential.potential.domain.member.fixture;

import four_tential.potential.domain.member.instructor_member.InstructorMember;

import java.util.UUID;

public class InstructorMemberFixture {

    public static final UUID DEFAULT_MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");
    public static final String DEFAULT_CATEGORY_CODE = "BACKEND";
    public static final String DEFAULT_CONTENT = "저는 5년 경력의 백엔드 개발자로 스프링 부트를 전문적으로 가르칩니다";
    public static final String DEFAULT_IMAGE_URL = "https://cdn.example.com/images/instructor/cert.jpg";
    public static final String DEFAULT_REJECT_REASON = "자격 요건이 충족되지 않습니다";

    public static InstructorMember defaultInstructorMember() {
        return InstructorMember.register(DEFAULT_MEMBER_ID, DEFAULT_CATEGORY_CODE, DEFAULT_CONTENT, DEFAULT_IMAGE_URL);
    }
}
