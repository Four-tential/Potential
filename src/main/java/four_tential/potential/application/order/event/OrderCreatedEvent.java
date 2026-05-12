package four_tential.potential.application.order.event;

import java.util.UUID;

public record OrderCreatedEvent(UUID orderId) {
}
