package four_tential.potential.presentation.member.model.request;

import four_tential.potential.domain.member.member_onboard.MemberOnBoardGoal;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UpdateOnBoardRequest(
        MemberOnBoardGoal goal,
        List<@NotBlank(message = "카테고리 코드를 입력해주세요")String> categoryCodes
) {
}
