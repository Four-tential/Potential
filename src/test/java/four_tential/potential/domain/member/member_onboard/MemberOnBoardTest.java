package four_tential.potential.domain.member.member_onboard;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.member.fixture.MemberFixture;
import four_tential.potential.domain.member.fixture.MemberOnBoardFixture;
import four_tential.potential.domain.member.member.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberOnBoardTest {

    @Test
    @DisplayName("register()로 생성하면 member와 goal이 설정")
    void register() {
        Member member = MemberFixture.defaultMember();

        MemberOnBoard memberOnBoard = MemberOnBoard.register(member, MemberOnBoardGoal.HOBBY);

        assertThat(memberOnBoard.getMember()).isEqualTo(member);
        assertThat(memberOnBoard.getGoal()).isEqualTo(MemberOnBoardGoal.HOBBY);
    }

    @Test
    @DisplayName("생성된 온보딩은 id가 null")
    void registerInitialState() {
        MemberOnBoard memberOnBoard = MemberOnBoardFixture.defaultMemberOnBoard();

        assertThat(memberOnBoard.getId()).isNull();
    }

    @Test
    @DisplayName("updateGoal()을 호출하면 goal이 변경")
    void updateGoal() {
        MemberOnBoard memberOnBoard = MemberOnBoardFixture.defaultMemberOnBoard();

        memberOnBoard.updateGoal(MemberOnBoardGoal.SELF_DEVELOPMENT);

        assertThat(memberOnBoard.getGoal()).isEqualTo(MemberOnBoardGoal.SELF_DEVELOPMENT);
    }

    @Test
    @DisplayName("모든 MemberOnBoardGoal 값으로 register 체크")
    void registerWithAllGoals() {
        Member member = MemberFixture.defaultMember();

        for (MemberOnBoardGoal goal : MemberOnBoardGoal.values()) {
            MemberOnBoard memberOnBoard = MemberOnBoard.register(member, goal);
            assertThat(memberOnBoard.getGoal()).isEqualTo(goal);
        }
    }

    @Test
    @DisplayName("register() 시 member가 null이면 ServiceErrorException이 발생")
    void registerWithNullMember() {
        assertThatThrownBy(() -> MemberOnBoard.register(null, MemberOnBoardGoal.HOBBY))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("회원을 입력해주세요");
    }

    @Test
    @DisplayName("register() 시 goal이 null이면 ServiceErrorException이 발생")
    void registerWithNullGoal() {
        Member member = MemberFixture.defaultMember();

        assertThatThrownBy(() -> MemberOnBoard.register(member, null))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("목표를 입력해주세요");
    }

    @Test
    @DisplayName("updateGoal() 시 goal이 null이면 ServiceErrorException이 발생")
    void updateGoalWithNull() {
        MemberOnBoard memberOnBoard = MemberOnBoardFixture.defaultMemberOnBoard();

        assertThatThrownBy(() -> memberOnBoard.updateGoal(null))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("목표를 입력해주세요");
    }
}
