package four_tential.potential.domain.member.fixture;

import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member_onboard.MemberOnBoard;
import four_tential.potential.domain.member.member_onboard.MemberOnBoardGoal;

public class MemberOnBoardFixture {

    public static final MemberOnBoardGoal DEFAULT_GOAL = MemberOnBoardGoal.HOBBY;

    public static MemberOnBoard defaultMemberOnBoard() {
        Member member = MemberFixture.defaultMember();
        return MemberOnBoard.register(member, DEFAULT_GOAL);
    }

    public static MemberOnBoard memberOnBoardWithGoal(MemberOnBoardGoal goal) {
        Member member = MemberFixture.defaultMember();
        return MemberOnBoard.register(member, goal);
    }
}
