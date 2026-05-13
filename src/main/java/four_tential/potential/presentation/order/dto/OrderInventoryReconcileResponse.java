package four_tential.potential.presentation.order.dto;

import java.util.UUID;

public record OrderInventoryReconcileResponse(
        UUID courseId,
        int totalCapacity,
        int dbOccupiedSeats,
        long reconciledCapacity
) {
    public static OrderInventoryReconcileResponse of(UUID courseId, int totalCapacity, int occupiedSeats, long reconciledCapacity) {
        return new OrderInventoryReconcileResponse(
        courseId, 
        totalCapacity, 
        occupiedSeats, 
        reconciledCapacity);
    }
}
