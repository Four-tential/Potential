package four_tential.potential.presentation.order.dto;

import java.math.BigInteger;
import java.util.UUID;

public record OrderCreateRequest(
        UUID courseId,
        int orderCount,
        BigInteger priceSnap,
        String titleSnap
) {
}
