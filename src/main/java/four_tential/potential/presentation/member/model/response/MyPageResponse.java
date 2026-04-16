package four_tential.potential.presentation.member.model.response;

import four_tential.potential.domain.member.member.Member;

import java.time.LocalDateTime;
import java.util.UUID;

public record MyPageResponse(
        UUID memberId,
        String email,
        String name,
        String phone,
        String role,
        String status,
        String profileImageUrl,
        LocalDateTime createdAt
) {
    public static MyPageResponse register(Member member, String profileImageUrl) {
        return new MyPageResponse(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.getPhone(),
                member.getRole().name(),
                member.getStatus().name(),
                profileImageUrl,
                member.getCreatedAt()
        );
    }
}
