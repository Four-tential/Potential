package four_tential.potential.presentation.member.model.request;

import four_tential.potential.domain.member.member_onboard.MemberOnBoardGoal;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OnBoardRequest(
        @NotNull(message = "목표를 입력해주세요")
        MemberOnBoardGoal goal,

        @NotEmpty(message = "카테고리를 하나 이상 입력해주세요")
        List<String> categoryCodes
) {
}
