package four_tential.potential.presentation.member.model.response;

import four_tential.potential.domain.member.member.Member;

import java.time.LocalDateTime;
import java.util.UUID;

public record UpdateMyPageResponse(
        UUID memberId,
        String name,
        String phone,
        String profileImageUrl,
        LocalDateTime updatedAt
) {
    public static UpdateMyPageResponse register(Member member, String profileImageUrl) {
        return new UpdateMyPageResponse(
                member.getId(),
                member.getName(),
                member.getPhone(),
                profileImageUrl,
                member.getUpdatedAt()
        );
    }
}
