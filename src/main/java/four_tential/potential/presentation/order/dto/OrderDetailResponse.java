package four_tential.potential.presentation.order.dto;

import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderStatus;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

public record OrderDetailResponse(
        UUID orderId,
        UUID courseId,
        String titleSnap,
        int orderCount,
        BigInteger priceSnap,
        BigInteger totalPriceSnap,
        OrderStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime expireAt
) {
    public static OrderDetailResponse from(Order order) {
        return new OrderDetailResponse(
                order.getId(),
                order.getCourseId(),
                order.getTitleSnap(),
                order.getOrderCount(),
                order.getPriceSnap(),
                order.getTotalPriceSnap(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getExpireAt()
        );
    }
}
