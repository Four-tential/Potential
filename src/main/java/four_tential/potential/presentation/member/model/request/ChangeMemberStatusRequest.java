package four_tential.potential.presentation.member.model.request;

import four_tential.potential.domain.member.member.MemberStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeMemberStatusRequest(

        @NotNull(message = "변경할 상태를 입력해주세요")
        MemberStatus status
) {
}
