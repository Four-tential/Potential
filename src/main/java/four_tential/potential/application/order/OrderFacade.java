package four_tential.potential.application.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;
    private final WaitingListService waitingListService;

}
