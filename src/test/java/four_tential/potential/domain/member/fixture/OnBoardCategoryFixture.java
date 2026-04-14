package four_tential.potential.domain.member.fixture;

import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.onboard_category.OnBoardCategory;

public class OnBoardCategoryFixture {

    public static final String DEFAULT_CATEGORY_CODE = "BACKEND";

    public static OnBoardCategory defaultOnBoardCategory() {
        Member member = MemberFixture.defaultMember();
        return OnBoardCategory.register(member, DEFAULT_CATEGORY_CODE);
    }

    public static OnBoardCategory onBoardCategoryWithCode(String categoryCode) {
        Member member = MemberFixture.defaultMember();
        return OnBoardCategory.register(member, categoryCode);
    }
}
