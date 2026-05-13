package four_tential.potential.presentation.order.dto;

import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderStatus;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

public record OrderMyListResponse(
        UUID orderId,
        UUID courseId,
        String titleSnap,
        BigInteger totalPriceSnap,
        OrderStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime expireAt
) {
    public static OrderMyListResponse from(Order order) {
        return new OrderMyListResponse(
                order.getId(),
                order.getCourseId(),
                order.getTitleSnap(),
                order.getTotalPriceSnap(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getExpireAt()
        );
    }
}
