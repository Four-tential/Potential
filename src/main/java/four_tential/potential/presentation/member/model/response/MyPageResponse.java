package four_tential.potential.presentation.member.model.response;

import four_tential.potential.domain.member.member.Member;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

public record MyPageResponse(
        @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") UUID memberId,
        @Schema(example = "user@example.com") String email,
        @Schema(example = "홍길동") String name,
        @Schema(example = "010-1234-5678") String phone,
        @Schema(example = "STUDENT") String role,
        @Schema(example = "ACTIVE") String status,
        @Schema(example = "https://cdn.example.com/profile-image/3fa85f64-5717-4562-b3fc-2c963f66afa6/abc123.jpg") String profileImageUrl,
        @Schema(example = "2025-01-15T09:00:00") LocalDateTime createdAt
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
