package four_tential.potential.presentation.order.dto;

import four_tential.potential.domain.order.WaitingStatus;
import java.util.UUID;

public record WaitingRoomEventResponse(
        UUID courseId,
        UUID memberId,
        Long rank,
        int totalWaitingCount,
        WaitingStatus status,
        String message
) {
    public static WaitingRoomEventResponse waiting(UUID courseId, UUID memberId, Long rank, int totalWaitingCount) {
        return new WaitingRoomEventResponse(
                courseId,
                memberId,
                rank,
                totalWaitingCount,
                WaitingStatus.WAITING,
                "현재 대기 순번은 " + rank + "번입니다"
        );
    }

    public static WaitingRoomEventResponse promoted(UUID courseId, UUID memberId) {
        return new WaitingRoomEventResponse(
                courseId,
                memberId,
                0L,
                0,
                WaitingStatus.CALLED,
                "주문 페이지로 이동합니다"
        );
    }
}
