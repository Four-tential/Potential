package four_tential.potential.presentation.member.model.response;

import four_tential.potential.domain.member.member.Member;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChangeMemberStatusResponse(
        UUID memberId,
        String status,
        LocalDateTime updatedAt
) {
    public static ChangeMemberStatusResponse register(Member member) {
        return new ChangeMemberStatusResponse(
                member.getId(),
                member.getStatus().name(),
                member.getUpdatedAt()
        );
    }
}
