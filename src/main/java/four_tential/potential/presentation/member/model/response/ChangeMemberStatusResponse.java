package four_tential.potential.presentation.member.model.response;

import four_tential.potential.domain.member.member.Member;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChangeMemberStatusResponse(
        @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") UUID memberId,
        @Schema(example = "SUSPENDED") String status,
        @Schema(example = "2025-06-01T10:00:00") LocalDateTime updatedAt
) {
    public static ChangeMemberStatusResponse register(Member member) {
        return new ChangeMemberStatusResponse(
                member.getId(),
                member.getStatus().name(),
                member.getUpdatedAt()
        );
    }
}
