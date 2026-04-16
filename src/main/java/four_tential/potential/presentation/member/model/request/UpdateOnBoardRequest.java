package four_tential.potential.presentation.member.model.request;

import four_tential.potential.domain.member.member_onboard.MemberOnBoardGoal;

import java.util.List;

public record UpdateOnBoardRequest(
        MemberOnBoardGoal goal,
        List<String> categoryCodes
) {
}
