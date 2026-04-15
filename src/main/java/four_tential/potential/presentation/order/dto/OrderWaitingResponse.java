package four_tential.potential.presentation.order.dto;

import java.util.UUID;

public record OrderWaitingResponse(
        UUID courseId,
        String status,
        String message
) implements OrderPlaceResult{
}
