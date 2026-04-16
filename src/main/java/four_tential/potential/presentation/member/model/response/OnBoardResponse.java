package four_tential.potential.presentation.member.model.response;

import four_tential.potential.domain.member.member_onboard.MemberOnBoard;

import java.time.LocalDateTime;
import java.util.List;

public record OnBoardResponse(
        String goal,
        List<String> categoryCodes,
        LocalDateTime createdAt
) {
    public static OnBoardResponse register(MemberOnBoard onBoard, List<String> categoryCodes) {
        return new OnBoardResponse(
                onBoard.getGoal().name(),
                categoryCodes,
                onBoard.getCreatedAt()
        );
    }
}
