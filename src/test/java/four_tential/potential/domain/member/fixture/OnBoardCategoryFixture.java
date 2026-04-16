package four_tential.potential.domain.member.fixture;

import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.onboard_category.MemberOnBoardCategory;

public class OnBoardCategoryFixture {

    public static final String DEFAULT_CATEGORY_CODE = "BACKEND";

    public static MemberOnBoardCategory defaultOnBoardCategory() {
        Member member = MemberFixture.defaultMember();
        return MemberOnBoardCategory.register(member, DEFAULT_CATEGORY_CODE);
    }

    public static MemberOnBoardCategory onBoardCategoryWithCode(String categoryCode) {
        Member member = MemberFixture.defaultMember();
        return MemberOnBoardCategory.register(member, categoryCode);
    }
}
